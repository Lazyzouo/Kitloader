# Security Policy / 安全政策

## Supported versions / 支持版本

Security fixes target the latest Release on the `main` branch. The tested server baseline is Paper/Folia 1.21.11 with Java 21.

安全修复只针对 `main` 分支的最新 Release。测试服务端基线为 Paper/Folia 1.21.11 与 Java 21。

## Reporting a vulnerability / 报告漏洞

Do not publish exploitable details in a public issue. Use GitHub's private security advisory for this repository, including server version, Java version, reproduction steps, impact, and relevant logs with player/IP secrets removed.

请勿在公开 Issue 中披露可利用细节。请通过本仓库 GitHub 私密安全公告报告，并提供服务端版本、Java 版本、复现步骤、影响范围和已删除玩家/IP 隐私信息的日志。

## Data handling / 数据处理

Kitloader has no telemetry, backdoor, or remote server-data collection path. Its update check accesses only GitHub's official Release API and optional download URL. Review the source in `main` before deployment and obtain binaries only from [official Releases](https://github.com/Lazyzouo/Kitloader/releases).

Kitloader 没有遥测、后门或远程收集服务器数据的路径。更新检查只访问 GitHub 官方 Release API 与可选下载地址。部署前可审查 `main` 源码，二进制文件只应从[官方 Releases](https://github.com/Lazyzouo/Kitloader/releases)获取。
