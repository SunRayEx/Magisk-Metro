use super::db::RootSettings;
use crate::daemon::{AID_APP_START, AID_ROOT, MagiskD, to_app_id, to_user_id};
use crate::db::{DbSettings, MultiuserMode};
use crate::ffi::{exec_root_shell, SuPolicy, SuRequest};
use crate::socket::IpcRead;
use base::{debug, error, exit_on_error, libc, warn, LoggedResult, ResultExt, WriteExt};
use std::os::fd::IntoRawFd;
use std::os::unix::net::{UCred, UnixStream};
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

#[allow(unused_imports)]
use std::os::fd::AsRawFd;

impl Default for SuRequest {
    fn default() -> Self {
        SuRequest {
            target_uid: AID_ROOT,
            target_pid: -1,
            keep_env: false,
            drop_cap: false,
            zero: false,
            command: vec![],
            context: "".to_string(),
            gids: vec![],
        }
    }
}

#[derive(Clone)]
pub struct SuInfo {
    pub(super) uid: i32,
    pub(super) eval_uid: i32,
    pub(super) settings: RootSettings,
    pub(super) mgr_uid: i32,
    cfg: DbSettings,
    timestamp: Instant,
}

impl Default for SuInfo {
    fn default() -> Self {
        SuInfo {
            uid: -1,
            eval_uid: -1,
            settings: Default::default(),
            mgr_uid: -1,
            cfg: Default::default(),
            timestamp: Instant::now(),
        }
    }
}

impl SuInfo {
    fn allow(uid: i32) -> SuInfo {
        let settings = RootSettings {
            policy: SuPolicy::Allow,
            ..Default::default()
        };
        SuInfo {
            uid,
            settings,
            ..Default::default()
        }
    }

    fn deny(uid: i32) -> SuInfo {
        SuInfo {
            uid,
            settings: RootSettings {
                policy: SuPolicy::Deny,
                ..Default::default()
            },
            ..Default::default()
        }
    }

    fn is_fresh(&self) -> bool {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|duration| duration.as_secs() as i64)
            .unwrap_or(i64::MAX);
        self.settings.policy != SuPolicy::Query
            && self.timestamp.elapsed() < Duration::from_secs(3)
            && (self.settings.until == 0 || self.settings.until > now)
    }
}

impl MagiskD {
    pub fn su_daemon_handler(&self, mut client: UnixStream, cred: UCred) {
        debug!(
            "su: request from uid=[{}], pid=[{}], client=[{}]",
            cred.uid,
            cred.pid.unwrap_or(-1),
            client.as_raw_fd()
        );

        let mut req = match client.read_decodable::<SuRequest>().log() {
            Ok(req) => req,
            Err(_) => {
                warn!("su: remote process probably died, abort");
                client.write_pod(&SuPolicy::Deny.repr).ok();
                return;
            }
        };

        let cached_info = self.get_su_info(cred.uid as i32);
        // Resolve prompts on a request-local copy. A one-shot answer must never enter the cache.
        let mut info = (*cached_info).clone();
        if info.settings.policy == SuPolicy::Query {
            info.settings.policy = self.request_app(&cred, &info);
        }

        self.notify_app(&cred, &info, &req);

        match info.settings.policy {
            SuPolicy::Restrict => req.drop_cap = true,
            SuPolicy::Zero => {
                req.zero = true;
                req.drop_cap = true;
            }
            SuPolicy::Allow => {}
            _ => {
                warn!("su: request rejected ({})", info.uid);
                client.write_pod(&SuPolicy::Deny.repr).ok();
                return;
            }
        }

        // At this point, the root access is granted.
        // Fork a child root process and monitor its exit value.
        let child = unsafe { libc::fork() };
        if child == 0 {
            debug!("su: fork handler");

            // Abort upon any error occurred
            exit_on_error(true);

            // ack
            client.write_pod(&0).ok();

            exec_root_shell(
                client.into_raw_fd(),
                cred.pid.unwrap_or(-1),
                &mut req,
                info.cfg.mnt_ns,
            );
            return;
        }
        if child < 0 {
            error!("su: fork failed, abort");
            return;
        }

        // Wait result
        debug!("su: waiting child pid=[{}]", child);
        let mut status = 0;
        let code = unsafe {
            if libc::waitpid(child, &mut status, 0) > 0 {
                libc::WEXITSTATUS(status)
            } else {
                -1
            }
        };
        debug!("su: return code=[{}]", code);
        client.write_pod(&code).ok();
    }

    fn get_su_info(&self, uid: i32) -> Arc<SuInfo> {
        if uid == AID_ROOT {
            return Arc::new(SuInfo::allow(AID_ROOT));
        }

        let cached = self.cached_su_info.load();
        if cached.uid == uid && cached.is_fresh() {
            return Arc::clone(&cached);
        }

        let info = self.build_su_info(uid);
        self.cached_su_info.store(info.clone());
        info
    }

    #[cfg(feature = "su-check-db")]
    fn build_su_info(&self, uid: i32) -> Arc<SuInfo> {
        let result = || -> LoggedResult<Arc<SuInfo>> {
            let cfg = self.get_db_settings()?;

            // Check multiuser settings
            let mut eval_uid = match cfg.multiuser_mode {
                MultiuserMode::OwnerOnly => {
                    if to_user_id(uid) != 0 {
                        return Ok(Arc::new(SuInfo::deny(uid)));
                    }
                    uid
                }
                MultiuserMode::OwnerManaged => to_app_id(uid),
                _ => uid,
            };
            if to_app_id(eval_uid) < AID_APP_START {
                eval_uid = to_app_id(eval_uid);
            }

            let mut settings = RootSettings {
                log: true,
                notify: true,
                ..Default::default()
            };
            self.get_root_settings(eval_uid, &mut settings)?;

            let mut mgr_uid = self.get_manager_uid(to_user_id(eval_uid));
            if mgr_uid < 0 && to_app_id(eval_uid) < AID_APP_START {
                for user in self.get_users() {
                    mgr_uid = self.get_manager_uid(user);
                    if mgr_uid >= 0 {
                        break;
                    }
                }
            }

            if mgr_uid < 0 {
                // A policy without a live manager cannot be authorized or audited. Do not
                // honor stale ALLOW/RESTRICT entries after the manager is removed.
                settings.log = false;
                settings.notify = false;
                settings.policy = SuPolicy::Deny;
            } else if to_app_id(uid) == to_app_id(mgr_uid) {
                // If it's the manager, allow it silently
                return Ok(Arc::new(SuInfo::allow(uid)));
            }

            // Finally, the SuInfo
            Ok(Arc::new(SuInfo {
                uid,
                eval_uid,
                settings,
                mgr_uid,
                cfg,
                timestamp: Instant::now(),
            }))
        }();

        result.unwrap_or(Arc::new(SuInfo::deny(uid)))
    }

    #[cfg(not(feature = "su-check-db"))]
    fn build_su_info(&self, uid: i32) -> Arc<SuInfo> {
        // A debug build without policy-database support must never turn into an implicit
        // global whitelist. Fail closed when policies cannot be checked.
        Arc::new(SuInfo::deny(uid))
    }
}
