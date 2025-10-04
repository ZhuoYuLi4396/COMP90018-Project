# Jinglin Lei - Progress

### 2025-08-18
- 完成注册页面的 Figma UI - Sign 页面 走查， 转换为 XML 布局文件 `activity_sign_up.xml`
- 设置资源：
    - 在 `/app/src/main/res/values/colors.xml` 定义主要颜色（主色、辅助色、文本颜色等）
    - 在 `/app/src/main/res/values/dimens.xml` 统一设置边距和文字大小
    - 在 `/app/src/main/res/values/strings.xml` 添加注册页面相关提示语（如 “Name”, “Email”, “Password”）
    - 在 `/app/src/main/res/values/styles.xml` 定义输入框和按钮的基础样式
    - 在 `/app/src/main/res/values/themes.xml` 调整应用主题，使之与 Figma 设计尽量保持一致
- 导入并应用 Figma UI 示例中的 Inter 字体（`/app/src/main/res/font/Inter`）
**Next:** 使用 Java 实现注册页面的逻辑class -  `SignUpActivity` 

### 2025-08-24
- 实现注册后端，包括正常注册流程、报错、密码设置最小长度8位。
- 暂时将启动页面设置为activity_sign-up.xml方便调试

### 2025-08-28
- 合并sign up sign in 功能

### 2025-09-04 -- 2025-09-15
实现Trip Page展示：Trip card实时更新；trip的搜索功能。
Add Trip
Trip Deatil 

### 2025-10-04
一个问题：Edit Trip是否能删除成员？如果那个成员还有没还完的账单怎么办？是否去掉删除成员的功能？