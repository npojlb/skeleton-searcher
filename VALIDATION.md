# 验证记录

已完成以下离线检查：

- 所有 JSON 资源文件通过语法解析。
- `build.sh` 通过 shell 语法检查。
- `SphereBand`、`SphereRegion`、`OperationMode`、`SphereMath` 使用 JDK 21 单独编译通过。
- 几何自检覆盖：相交球壳可找到共同整数方块、远距离球壳判定无交集、同心但半径区间分离的球壳判定无交集、并集/交集成员判断。
- 全部 Java 源码已使用一组与目标 Fabric/Yarn 方法签名对应的离线桩进行静态编译检查。

当前运行环境无法联网解析 Gradle、Minecraft、Fabric API 等真实依赖，因此未在此环境中产出或实机启动最终 JAR。项目内置 GitHub Actions 工作流，可在联网环境执行真实的 `gradle build`；首次构建后仍建议进入单人世界检查渲染效果和快捷键。
