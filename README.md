# پروژه مدیریت ساختمان – نسخه ۱.۱.۰

این بسته شامل سورس Kotlin و Jetpack Compose پروژه است. وابستگی به GitHub Actions حذف شده است.

## پیش‌نیاز ساخت APK
- Android Studio روی رایانه (نسخه پایدار)
- JDK 17
- Android SDK Platform 35 و Build Tools سازگار
- دسترسی اینترنت برای دریافت وابستگی‌های Gradle در اولین Sync

## ساخت
1. فایل ZIP را استخراج کنید.
2. پوشه `SakhtemanModir` را در Android Studio با گزینه Open باز کنید.
3. در صورت درخواست، Gradle JDK را روی JDK 17 قرار دهید.
4. اجازه دهید Gradle Sync و دریافت وابستگی‌ها کامل شود.
5. از منوی Build گزینه Build APK(s) را اجرا کنید.
6. خروجی معمولاً در `app/build/outputs/apk/debug/app-debug.apk` قرار می‌گیرد.

## نکته مهم درباره این بسته
در محیط فعلی Android SDK و Gradle نصب نیست؛ بنابراین امکان کامپایل و تحویل APK آزمایش‌شده از این محیط وجود نداشت. همچنین فایل Gradle Wrapper (از جمله wrapper JAR) در سورس اولیه موجود نبود. Android Studio ممکن است پیشنهاد ساخت/تنظیم Gradle Wrapper بدهد؛ در صورت درخواست، آن را بپذیرید و از نسخه Gradle سازگار با Android Gradle Plugin 8.6.1 استفاده کنید.

## مشخصات ثبت‌شده در سورس
- شناسه برنامه: `ir.ehsan.building`
- حداقل Android: API 24
- Compile/Target SDK: 35
- نسخه: 1.1.0 (versionCode 2)
- پایگاه داده محلی Room
- رابط کاربری Jetpack Compose / Material 3
- خروجی گزارش XLSX

اطلاعات برنامه به‌صورت محلی روی دستگاه ذخیره می‌شود؛ همگام‌سازی ابری در این نسخه پیاده‌سازی نشده است.
