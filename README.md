# Cảnh báo mưa công trình (Android)

Báo động trước 30/45/60 phút khi mưa sắp tới vị trí điện thoại, dựa trên radar thật (RainViewer, nguồn radar Việt Nam: Đài KTTV) và dự báo Open-Meteo.

- Không chạy dịch vụ thường trực. App hẹn giờ thức dậy mỗi 5–15 phút (càng có mưa gần càng kiểm tra dày), mỗi lần chạy vài giây rồi ngủ lại.
- Ảnh radar được lưu đệm: mỗi lần chỉ tải 1 ảnh mới (khoảng 1–4 ô bản đồ nhỏ).
- Không tự bật GPS khi chạy nền. App chỉ đọc vị trí máy đã có sẵn.
- Ngoài giờ làm việc app nghỉ hẳn.

Bản dựng: GitHub Actions (`.github/workflows/android.yml`) tự đóng gói APK và đăng ở Releases.
