use super::SuInfo;
use crate::consts::{APP_PACKAGE_NAME, INTERNAL_DIR, MAGISK_FILE_CON};
use crate::daemon::{AID_APP_START, MagiskD, to_app_id, to_user_id};
use crate::ffi::{SuPolicy, SuRequest, fork_dont_care, get_magisk_tmp};
use crate::socket::IpcRead;
use ExtraVal::{Bool, Int, IntList, Str};
use base::const_format::concatcp;
use base::{BytesExt, FileAttr, LoggedResult, ResultExt, Utf8CStrBuf, cstr, error};
use nix::fcntl::OFlag;
use nix::poll::{PollFd, PollFlags, PollTimeout};
use nix::sys::signal::SigSet;
use num_traits::AsPrimitive;
use std::fmt::Write;
use std::os::fd::AsFd;
use std::os::unix::net::UCred;
use std::os::unix::process::CommandExt;
use std::process::{Command, Stdio, exit};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

static REQUEST_ID: AtomicU64 = AtomicU64::new(0);

fn app_process() -> Command {
    let mut cmd = Command::new("/system/bin/app_process");
    // daemon thread blocks all signals, unblock it before starting app_process
    unsafe {
        cmd.pre_exec(|| SigSet::empty().thread_set_mask().map_err(Into::into));
    }
    cmd
}

struct Extra<'a> {
    key: &'static str,
    value: ExtraVal<'a>,
}

enum ExtraVal<'a> {
    Int(i32),
    Bool(bool),
    Str(&'a str),
    IntList(&'a [u32]),
}

impl Extra<'_> {
    fn add_bind(&self, cmd: &mut Command) {
        let mut tmp: String;
        match self.value {
            Int(i) => {
                tmp = format!("{}:i:{}", self.key, i);
            }
            Bool(b) => {
                tmp = format!("{}:b:{}", self.key, b);
            }
            Str(s) => {
                let s = s.replace("\\", "\\\\").replace(":", "\\:");
                tmp = format!("{}:s:{}", self.key, s);
            }
            IntList(list) => {
                tmp = format!("{}:s:", self.key);
                if !list.is_empty() {
                    list.iter().for_each(|i| {
                        write!(&mut tmp, "{i},").ok();
                    });
                    tmp.pop();
                }
            }
        }
        cmd.args(["--extra", &tmp]);
    }

    fn add_bind_legacy(&self, cmd: &mut Command) {
        match self.value {
            Str(s) => {
                let tmp = format!("{}:s:{}", self.key, s);
                cmd.args(["--extra", &tmp]);
            }
            _ => self.add_bind(cmd),
        }
    }
}

impl MagiskD {
    pub(super) fn request_app(&self, cred: &UCred, info: &SuInfo) -> SuPolicy {
        if info.mgr_uid < 0 || cred.pid.unwrap_or(-1) <= 0 {
            return SuPolicy::Deny;
        }
        let mut fifo = cstr::buf::new::<128>();
        if fifo.write_fmt(format_args!(
            "{}/{}/su_request_{}_{}", get_magisk_tmp(), INTERNAL_DIR,
            cred.pid.unwrap_or(-1), REQUEST_ID.fetch_add(1, Ordering::Relaxed)
        )).is_err() {
            return SuPolicy::Deny;
        }

        let result = || -> LoggedResult<SuPolicy> {
            let mut attr = FileAttr::new();
            attr.st.st_mode = 0o600;
            attr.st.st_uid = info.mgr_uid.as_();
            attr.st.st_gid = info.mgr_uid.as_();
            attr.con.push_str(MAGISK_FILE_CON);
            fifo.mkfifo(0o600)?;
            fifo.set_attr(&attr)?;
            // Nonblocking reads also fail closed if the writer sends an incomplete response.
            let mut fd = fifo.open(OFlag::O_RDWR | OFlag::O_CLOEXEC | OFlag::O_NONBLOCK)?;
            let mut cmd = app_process();
            cmd.args([
                "/system/bin", "com.android.commands.am.Am", "start",
                "-n", concatcp!(APP_PACKAGE_NAME, "/com.topjohnwu.magisk.ui.surequest.SuRequestActivity"),
                "--user", &to_user_id(info.mgr_uid).to_string(),
                "-a", "android.intent.action.VIEW", "-f", "0x18800020",
                "--es", "action", "request",
                "--es", "fifo", &fifo,
                "--ei", "uid", &info.eval_uid.to_string(),
                "--ei", "pid", &cred.pid.unwrap_or(-1).to_string(),
            ]);
            cmd.env("CLASSPATH", "/system/framework/am.jar");
            // ActivityManager can hang independently of the app. Bound startup as well as
            // the FIFO wait, and do not let unread command output fill a pipe.
            cmd.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
            let mut child = cmd.spawn().log()?;
            let started = Instant::now();
            loop {
                match child.try_wait() {
                    Ok(Some(status)) => {
                        if !status.success() {
                            return Ok(SuPolicy::Deny);
                        }
                        break;
                    }
                    Ok(None) if started.elapsed() < Duration::from_secs(10) => {
                        std::thread::sleep(Duration::from_millis(50));
                    }
                    _ => {
                        child.kill().ok();
                        child.wait().ok();
                        return Ok(SuPolicy::Deny);
                    }
                }
            }
            let mut pfd = [PollFd::new(fd.as_fd(), PollFlags::POLLIN)];
            if nix::poll::poll(&mut pfd, PollTimeout::try_from(70_000).unwrap_or(PollTimeout::ZERO)).log()? <= 0
                || !pfd[0].revents().unwrap_or(PollFlags::empty()).contains(PollFlags::POLLIN)
            {
                return Ok(SuPolicy::Deny);
            }
            let answer = i32::from_be(fd.read_decodable::<i32>().log()?);
            Ok(match answer {
                value if value == SuPolicy::Allow.repr => SuPolicy::Allow,
                value if value == SuPolicy::Restrict.repr => SuPolicy::Restrict,
                value if value == SuPolicy::Zero.repr => SuPolicy::Zero,
                _ => SuPolicy::Deny,
            })
        }();
        fifo.remove().log_ok();
        result.unwrap_or(SuPolicy::Deny)
    }

    fn exec_cmd(&self, user: &str, action: &'static str, extras: &[Extra]) {
        let mut cmd = app_process();
        cmd.args([
            "/system/bin",
            "com.android.commands.content.Content",
            "call",
            "--uri",
            concatcp!("content://", APP_PACKAGE_NAME, ".provider"),
            "--user",
            user,
            "--method",
            action,
        ]);
        if self.sdk_int() >= 30 {
            extras.iter().for_each(|e| e.add_bind(&mut cmd))
        } else {
            extras.iter().for_each(|e| e.add_bind_legacy(&mut cmd))
        }
        cmd.env("CLASSPATH", "/system/framework/content.jar");

        match cmd.output() {
            Ok(output) => {
                if output.stderr.contains(b"Error") || output.stdout.contains(b"Error") {
                    error!(
                        "content call failed: {}",
                        String::from_utf8_lossy(&output.stderr)
                    );
                }
            }
            Err(e) => {
                error!("failed to execute content call: {e}");
            }
        }
    }

    fn app_notify(&self, cred: &UCred, info: &SuInfo, request: &SuRequest) {
        let mut command = request.command.join(" ");
        let mut legacy_cmd = false;
        if request.command.len() >= 4
            && request.command[1] == "-c"
            && !request.command[2].contains(b" ")
        {
            command = format!("(Syntax Error) {}", command);
            legacy_cmd = true;
        }
        let extras = [
            Extra {
                key: "from.uid",
                value: Int(cred.uid.as_()),
            },
            Extra {
                key: "to.uid",
                value: Int(request.target_uid),
            },
            Extra {
                key: "pid",
                value: Int(cred.pid.unwrap_or(-1).as_()),
            },
            Extra {
                key: "policy",
                value: Int(info.settings.policy.repr),
            },
            Extra {
                key: "target",
                value: Int(request.target_pid),
            },
            Extra {
                key: "context",
                value: Str(&request.context),
            },
            Extra {
                key: "gids",
                value: IntList(&request.gids),
            },
            Extra {
                key: "command",
                value: Str(&command),
            },
            Extra {
                key: "legacy_cmd",
                value: Bool(legacy_cmd),
            },
            Extra {
                key: "notify",
                value: Bool(info.settings.notify),
            },
            Extra {
                key: "log",
                value: Bool(info.settings.log),
            },
        ];
        let user_id;
        let user = if to_app_id(info.eval_uid) < AID_APP_START {
            "current"
        } else {
            user_id = to_user_id(info.eval_uid).to_string();
            &user_id
        };
        self.exec_cmd(user, "notify", &extras);
    }

    pub(super) fn notify_app(&self, cred: &UCred, info: &SuInfo, request: &SuRequest) {
        if !info.settings.log && !info.settings.notify {
            return;
        }

        if fork_dont_care() != 0 {
            return;
        }

        // Notify su usage to application
        self.app_notify(cred, info, request);

        exit(0);
    }
}
