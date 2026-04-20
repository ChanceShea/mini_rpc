# mini_rpc

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.6%2B-red.svg)](https://maven.apache.org/)

**mini_rpc** 是一个轻量级、高性能的 Java RPC（远程过程调用）框架，支持泛化调用、可插拔的序列化与压缩算法。旨在简化分布式系统之间的服务调用，提供类似本地方法调用的体验。

## ✨ 特性

- **轻量级**：代码简洁，核心依赖少，易于集成和扩展。
- **泛化调用**：支持不依赖服务方接口API进行调用，特别适用于测试或网关场景。
- **可插拔组件**：
  - **序列化器**：支持 JSON、Hessian、Protobuf 等（可扩展）。
  - **压缩器**：支持 GZIP、Snappy 等（可扩展）。
- **基于 Java NIO**：采用高性能网络通信模型，支持多线程处理。
- **简单易用**：通过注解和配置文件即可快速发布和引用服务。

## 🛠 技术栈

- **开发语言**：Java (1.8+)
- **构建工具**：Maven
- **网络通信**：自定义协议 + Netty (NIO)
- **序列化**：FastJSON / Hessian / Protobuf
- **压缩**：GZIP / Snappy
- **动态代理**：JDK 动态代理或 Javassist

## 📦 快速开始

### 1. 添加依赖

目前项目未发布到 Maven 中央仓库，请先克隆并安装到本地：

```bash
git clone https://github.com/ChanceShea/mini_rpc.git
cd mini_rpc
mvn clean install
