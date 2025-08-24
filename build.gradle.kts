// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
//如果是想不使用firebase那么运行时报错先注释下面这一行
//如果要联动firebase建议使用firebase官方使用的id格式加json文件
//报错的原因是因为没用放json文件进去
    alias(libs.plugins.google.services) apply false // To Connect Firebase which is a Google service. Jinglin 8.18.2025
}
