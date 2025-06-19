# flutter_module
flutter 子模块
## Getting Started
## 引入方式1
直接通过aar引入：
```bash
flutter build aar
```
先通过上述命令构建aar，然后到settings.gradle设置一下即可：
```gradle
// Flutter repo
        maven {
            setUrl("../flutter_module/build/host/outputs/repo")
        }
        maven {
            setUrl("https://storage.googleapis.com/download.flutter.io")
        }
```
再到app下面的gradle文件添加依赖
```groovy
dependencies {

    implementation 'androidx.core:core-ktx:1.9.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.google.android.material:material:1.6.0'
    implementation 'androidx.appcompat:appcompat:1.4.1'
    implementation 'androidx.activity:activity:1.8.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'// 配套降级
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'

    implementation("com.google.code.gson:gson:2.6.2")
    implementation("com.github.Justson:Downloader:v5.0.4-androidx")
    // APP工程添加依赖
    implementation("com.example.flutter_module:flutter_release:1.0")
}
```
