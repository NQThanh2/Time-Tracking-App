# Time-Tracking-App
## Liên kết Google Sheets (1.2.0)

App hỗ trợ chọn tài khoản Google, tự tạo bảng riêng và tự gửi giờ vào/ra, bù công, tăng ca trên 8 tiếng/ngày và tổng tháng. Mất mạng vẫn lưu chấm công trên máy và gửi lại sau. Người dùng không cần tạo Apps Script.

Người phát hành cấu hình OAuth Android và bật Google Drive/Sheets API một lần theo [hướng dẫn Google Cloud](google-sheets/GOOGLE-CLOUD-SETUP.md). Sau đó người dùng chỉ cần chọn Gmail và cấp quyền trong app. Xem [cách dùng và giới hạn đồng bộ](google-sheets/README.md).

## Tính năng ban đầu

v1.0.0 - Điểm danh vân tay
Ứng dụng chấm công offline chạy trực tiếp trên máy, không cần server hay kết nối mạng — mọi dữ liệu lưu cục bộ trên thiết bị.

Tính năng chính
Chấm công bằng vân tay: Check-in / Check-out xác thực qua sinh trắc học (biometric) của thiết bị
Lưu trữ offline: Toàn bộ lịch sử chấm công lưu local (SQLite), không phụ thuộc internet
Tổng hợp theo ngày & tháng: Xem giờ làm trong ngày, tổng giờ công theo tháng, điều hướng qua lại giữa các tháng
Tính lương tự động: Nhập lương theo giờ, app tự tính lương tháng dựa trên số giờ đã chấm công
Phụ cấp gửi xe: Ghi nhận phí gửi xe theo lượt, cộng dồn vào chi phí tháng
Bù công (catch-up): Bổ sung check-in/check-out cho hôm nay hoặc ngày trước đó nếu quên chấm công
Xem theo lịch: Giao diện lịch trực quan cho từng ngày trong tháng
Yêu cầu
Android (minSdk 30 trở lên)
Thiết bị hỗ trợ cảm biến vân tay / sinh trắc học
Ghi chú cài đặt
File APK đính kèm bên dưới. Nếu máy chặn cài từ nguồn ngoài Play Store, vào Cài đặt → Bảo mật → Cho phép cài từ nguồn không xác định trước khi cài.
