<p align="center">
  <img src="docs/images/logo.png" width="320" alt="MagisKube">
</p>

# MagisKube · Magisk Metro

**Magisk 的 Metro 重制版。** 管理器 UI 从零重写为 Windows 8 / Windows Phone 设计语言(磁贴开始屏幕、Pivot 导航、七色角色强调色系统),并在此基础上扩展了原生功能:**常驻模块**(nix 式声明式模块管理,Rust + Zig + C 原生栈)与**动态授权**(单次授权 / 0 级授权 / DenyList 沙箱加强)。

上游的全部核心能力原样保留:**MagiskSU** root 授权、**Magisk Modules** 无系统分区修改、**MagiskBoot** 镜像解包重打包、**Zygisk** 进程注入。

---

## 设计语言:一场 Metro UI 重写

### 开始屏幕是一块真正的磁贴板

主页不再是"列表 + 卡片",而是一块 Windows 开始屏幕式的**磁贴板**:

- **真实网格布局引擎**——每个磁贴都是布局引擎管理下的矩形,支持 3×6 / 4×6 / 4×8 三种基底网格;手机端可在"自定义开始屏幕磁贴"模式里**拖动重排**(长按拖拽、目标悬停两秒自动腾位)、**四角手柄直接缩放**,所有操作由打包算法保证磁贴永不重叠;
- **内容即磁贴**——Magisk 版本状态、模块滚动列表、已授权应用、日志告警直接"长"在磁贴上,而不是藏在图标后面;带自动滚动 ticker 的磁贴让信息自己流动;
- **磁贴组**——把 2 或 4 个应用合并成一块磁贴,点开是独立的 Metro 二级页面,逐个列出组内应用;
- **自定义磁贴**——任意已授权应用可以创建 1×1 / 2×1 / 2×2 磁贴,自定义名称、滚动内容与颜色(默认取当前主题强调色)。

### 七角色强调色,贯穿每一层界面

Metro 的灵魂是扁平的大色块。本项目建立了一套**七角色强调色系统**:Magisk(绿)、模块(蓝)、应用(红)、设置(黄)、贡献者(紫)、日志(白)、赞助(粉)。从开始屏幕的磁贴,到点进去的二级页面、菜单图标、开关、弹窗、选择器,**每一层界面都继承来源磁贴的颜色**——从"模块"磁贴进入的页面是蓝色的,弹出的对话框也是蓝色的。View 层通过布局 `android:tag` 声明式染色,Compose 层通过 `LocalMetroPalette` 提供同一套颜色,两套渲染管线共享同一个取色源,永不漂移。

主题系统提供:内置 Metro 配色、**Material You 壁纸取色**(弹窗、选择器等全部 View 层同样生效,不受 OEM 动态色白名单限制)、**自定义主题**(逐角色 HEX 调色)、以及若干经典打包主题。

### Pivot 二级导航

点开磁贴后进入的是 Windows Phone 式 **Pivot 页面**:超大标题横向排布,标题拖拽、内容横滑、点击切换三位一体,同一 Pivot 内横向滑动即可在关联的二级视图间流转(如"已安装 / 可更新"、"授权 / 排除")。设置页本身就是一组 Pivot。

### 弹窗也是 Metro 的

弹窗为直角、无阴影的 Metro 对话框,入场为绕顶边翻转的"转门"动画;着色同样跟随当前磁贴角色。

### 全局 Metro 密度

整个应用以 0.75× 显示密度渲染,带来典型 Windows 设备的"信息密度感",View 与 Compose 一并生效。

## 流畅体验

动效不是一个"附加物",而是信息结构的一部分:

- **磁贴弹簧几何**——拖动与缩放的所有几何变化由弹簧动画驱动:每一步重定位都从当前的位置**和速度**出发,邻居磁贴自然地被挤压、舒展,没有"跳格"感;缩放手柄与磁贴同乘一条弹簧,永不脱节;
- **转门翻转 + 错开编排**——列表与页面元素以绕顶边翻转的方式依次入场(Windows Phone 转门效果),错开节奏取自磁贴飞行的节拍;
- **返回即倒放**——二级页面返回开始屏幕时,入场动画被原样倒放:内容按相反顺序翻转退场,整页向右滑出淡出,同时身后磁贴板的磁贴逐块飞回原位;一个完整的"磁贴世界"往返;
- **状态栏与页面同色**——进入分区时状态栏染上该分区强调色,返回主页即恢复;
- 所有缓动曲线集中在 `MetroEaseOut`(快启动、缓落定)一处,全局节奏一致。

## 常驻模块:不可变、可重现的模块声明

一个 nix 风格的功能:**用一份声明式 JSON 清单描述"我的设备应该有哪些模块"**,并由原生链接管理器在每次开机时把现实纠正为声明。

- **固定(Pin)**:把已安装模块快照进不可变存储 `/data/adb/metromod/store/<id>/`;
- **恢复**:开机时(post-fs-data)自动恢复被误删的固定模块、重新启用被意外停用的固定模块——误删不再可能;
- **GUI 优先**:模块页顶部"常驻"页签提供概览与入口;完整的 Metro 风格管理页(开关即固定/解除固定、立即应用、说明文档)位于 **设置 · 杂项 → 配置常驻模块**;
- **清单即文档**:`/data/adb/metromod/manifest.json` 人类可读、可编辑、可备份,移植到新设备只需拷贝目录。

原生栈分工明确:

| 组件 | 语言 | 职责 |
|---|---|---|
| `metroconf` | **Rust** | 声明式清单的 JSON 解析、校验、序列化(零依赖,C ABI) |
| `metrolink` 调度 | **Zig** | 命令分发与清单协调(apply / snapshot / unpin / toggle / prune) |
| `metrolink` 文件层 | **C** | 递归快照复制、原子写入、module.prop 解析 |

最终产物是一个全静态链接的 `metrolink` 可执行文件(4 个 ABI),随 APK 分发、首次使用时自动安装到 `/data/adb/metromod/`,并通过版本戳自愈机制保证与 APK 版本一致。

## 动态授权(实验性)

- **单次授权**:su 弹窗中的"仅本次"——授权只对当次请求生效,不写入策略数据库,永不出现授权列表;
- **0 级授权**:欺骗性授予——shell 显示 uid 0,但运行在独立挂载命名空间 + 用户空间隐藏 + 全能力清除 + **Seccomp BPF** 拦截之下,高危系统调用(mount / ptrace / bpf / kexec…)一律失败;
- **幽灵审计**:被拦截的系统调用以目标进程完全不可见的方式记录到 `/data/adb/metromod/ghost_audit.log`,su 日志页可查看;
- **DenyList 沙箱**:可为排除列表应用套用同一 Seccomp 沙箱,拦截事件同样进入幽灵审计。

> 0 级授权与 DenyList 沙箱触及 magiskd / zygisk 核心路径,属于实验性功能,欢迎在真机上反馈实验结果。

---

## 构建

依赖:Python 3.8+、JDK 21、Android SDK,以及:

```bash
# 安装 Magisk 定制 NDK(内含交叉 Rust 工具链)
./build.py ndk
# 需要系统安装 zig(Z + C 链接管理器)
```

```bash
./build.py binary          # 原生组件(含 metrolink)
./build.py app             # 管理器 APK(输出到 out/)
./build.py all             # 全量构建
```

关键目录:

```
app/apk/.../ui/home/       开始屏幕、磁贴布局引擎、磁贴组
app/apk/.../ui/pivot/      Pivot 导航与全部 Metro 分区
app/apk/.../ui/theme/      七角色强调色系统、主题、取色管线
app/core/.../PersistentModules.kt   常驻模块应用侧桥接
native/src/metroconf/      Rust 清单解析器(单元测试)
native/src/metro/link/     Zig 调度 + C 文件层(metrolink)
native/src/core/su/ghost.cpp  Seccomp BPF 幽灵沙箱
```

## 支持与反馈

- 问题反馈与代码:[GitHub - SunRayEx/Magisk-Metro](https://github.com/SunRayEx/Magisk-Metro)
- [爱发电赞助](https://afdian.com/a/SunRayEx)

## 致谢与许可

基于 [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk) 构建,感谢上游与所有模块开发者。Metro UI 重写、常驻模块与动态授权部分由 SunRayEx 编写。

    Magisk, including all git submodules are free software:
    you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation,
    either version 3 of the license, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
