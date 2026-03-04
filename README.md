iarnet 控制平面需要先安装 iarnet-api，如果通过 docker 部署则可以忽略这个问题


与论文无关的：

整理项目代码结构，和代码仓库。

与论文有关的：

ir 的设计，dsl 的设计，工作流提交的流程，平台有哪些组件（从上到下），通信机制，资源管理机制，一些容错机制。核心模型的设计。突出强调创新点和可以引用参考文献的部分。调度算法。gossip 算法和模型设计。

adapter 注册时参考 cloudflare 不一定要提供公网 ip。

类似的工业实践
Kubernetes: kubelet 主动连 API Server，API Server 不直接连 kubelet（exec/logs 除外）
Terraform Cloud Agent: Agent 主动拉取任务
CloudFlare Tunnel: 边缘节点主动建立到中心的隧道
这个方案和你之前提到的 ICE 式通信也是一脉相承的思路——连接方向由"能出站的一方"发起。

维度	当前设计（双 gRPC Server）	Command Channel
Adapter 需要公网 IP	是	否
Adapter 暴露端口	需要	不需要
NAT/防火墙穿越	不支持	天然支持
实现复杂度	简单（独立 RPC）	中等（需要命令封装/分发）
连接管理	无状态	需维护长连接 + 断线重连