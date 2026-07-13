# Skeleton Searcher（Fabric 1.21.11）

客户端球壳定位辅助模组。它不会在世界中真实放置方块，只会把命中区域绘制为白色半透明实心方块。

## 功能

- 五种球壳：250–200、200–150、150–100、100–50、50–25。
- 交集模式：只显示全部球壳共同区域；新球体会使共同区域为空时提示“已超出区域”。
- 并集模式：显示所有球壳区域。
- 三球后显示模式：前两次添加球体只保存并参与交集计算，不渲染；第三个及以后球体加入后开始显示共同区域。
- O：打开中文设置页面。
- Delete：清除全部球体。
- 可调透明度和本地渲染范围。
- 配置文件：`config/skeleton_searcher.json`。

## 构建

项目已包含 `.github/workflows/build.yml`。上传到 GitHub 后，在 Actions 中运行 Build，成功后从 Summary 页底部下载 `skeleton-searcher-1.21.11` Artifact，解压得到：

`Skeleton Searcher-1.1.0.jar` 或 `skeleton-searcher-1.1.0.jar`（实际名称取决于 Gradle 对 archivesName 的处理）。

将非 `-sources.jar` 文件放入 `.minecraft/mods`。

## 性能说明

实心方块比仅绘制外表面消耗更高。渲染器只计算玩家附近范围，并将单次显示上限设为 350,000 个方块。若日志提示达到上限，降低渲染范围或增加交集球体即可。
