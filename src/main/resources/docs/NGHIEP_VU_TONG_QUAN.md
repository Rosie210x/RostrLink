# Tổng Quan Nghiệp Vụ Dự Án RostrLink

## 1. Mục tiêu hệ thống
RostrLink là hệ thống backend quản lý hoạt động đón/trả học sinh và điểm danh theo nhiều vai trò trong môi trường trường học.

Mục tiêu chính:
- Quản trị danh mục người dùng, lớp học, học sinh, phụ huynh, giáo viên, tài xế.
- Theo dõi sự kiện di chuyển/hoạt động liên quan đến học sinh.
- Ghi nhận log điểm danh theo nhiều ngữ cảnh (phụ huynh, giáo viên, học sinh, tài xế).
- Phát sinh cảnh báo (alert) và cấu hình cảnh báo.
- Quản lý nghiệp vụ thanh toán, gói dịch vụ và thiết lập hệ thống.

## 2. Vai trò nghiệp vụ
Hệ thống hỗ trợ các userRole:
- `Admin`, `User`: quản trị dữ liệu và cấu hình hệ thống.
- `Parent`: theo dõi học sinh, log, cảnh báo, xử lý trạng thái tham gia sự kiện.
- `Teacher`: quản lý học sinh/lớp phụ trách, điểm danh học sinh, theo dõi sự kiện.
- `Driver`: ghi nhận đón/trả, theo dõi sự kiện tuyến, gửi thông báo đã đến.
- `Student`: vai trò dữ liệu trong một số ngữ cảnh phân quyền/xác thực.
- `Kiosk`: thiết bị đầu cuối để ghi nhận điểm danh.

## 3. Nhóm nghiệp vụ chính

### 3.1 Xác thực & phiên đăng nhập
- Đăng nhập theo userRole.
- Quên mật khẩu qua OTP, xác minh OTP, reset mật khẩu.
- Refresh token, logout, lấy thông tin tài khoản hiện tại.

### 3.2 Quản lý danh mục
- CRUD cho các thực thể: `Users`, `Parents`, `Teachers`, `Drivers`, `Students`, `Classes`, `NfcTags`, `Wands`, `Kiosks`, `Packages`, `Settings`.
- Dữ liệu có quan hệ chéo: học sinh - phụ huynh - giáo viên - lớp.

### 3.3 Điểm danh & log nghiệp vụ
- `TeacherLog`: giáo viên check-in/check-out.
- `StudentLog`: giáo viên ghi nhận check-in/check-out cho học sinh, truy vấn theo ngày/trạng thái.
- `ParentLog`: điểm danh tại kiosk theo phụ huynh/học sinh.
- `DriverLog`: tài xế ghi nhận đón/trả học sinh.
- Các log đều có API list + detail và API nghiệp vụ ghi nhận tương ứng.

### 3.4 Sự kiện (đưa đón/ngoại khóa)
- Quản lý event: tạo/sửa/xóa, bắt đầu/kết thúc, phân công tài xế.
- Quản lý danh sách học sinh theo event (`event_students`).
- Phụ huynh có thể chấp nhận/từ chối tham gia event cho học sinh.
- Theo dõi event theo userRole: giáo viên, tài xế, phụ huynh.

### 3.5 Cảnh báo (Alert)
- Truy vấn danh sách cảnh báo, cảnh báo gần nhất, chi tiết cảnh báo theo vai trò.
- Tài xế có luồng tạo alert "đã đến" gửi cho danh sách phụ huynh.
- Có module `alert-settings` để cấu hình hành vi cảnh báo.

### 3.6 Thanh toán & gói dịch vụ
- Quản lý `payment_bills` (đọc danh sách/chi tiết hóa đơn).
- Quản lý `packages` cho nghiệp vụ gói dịch vụ.

## 4. Luồng nghiệp vụ điển hình
1. Admin/User cấu hình danh mục (lớp, giáo viên, học sinh, phụ huynh, tài xế, thiết bị).
2. Hệ thống tạo sự kiện đưa đón có danh sách học sinh tham gia.
3. Các bên thực hiện điểm danh theo ngữ cảnh (`teacher`, `driver`, `kiosk`) và sinh bản ghi log.
4. Khi có trigger nghiệp vụ (ví dụ tài xế đã đến điểm đón), hệ thống phát alert đến các đối tượng liên quan.
5. Dashboard và các API tổng hợp phục vụ theo dõi trạng thái theo userRole.

## 5. Dữ liệu cốt lõi (tham chiếu nhanh)
- Người dùng & phân quyền: `users`, `tokens`, `otp_tokens`.
- Danh mục học đường: `parents`, `teachers`, `drivers`, `students`, `classes`, `student_classes`.
- Nghiệp vụ event: `events`, `event_students`.
- Cảnh báo: `alerts`, `alert_targets`, `alert_settings`.
- Điểm danh/log: `parent_logs`, `teacher_logs`, `student_logs`, `driver_logs`.
- Thiết bị: `nfc_tags`, `wands`, `kiosks`.
- Dịch vụ/thanh toán: `packages`, `payment_bills`.

## 6. Ghi chú triển khai
- API được mô tả tại `src/RostrLink/RostrLink.Api/docs/swagger.yml`.
- Response dùng envelope `JsonResponse` (`status`, `message`, `data`, `errorCode`, `errorData`).
- Hệ thống dùng soft-delete cho phần lớn dữ liệu (`deleted_at`).
- Có tích hợp hạ tầng AWS (S3/SNS/SES) và Hangfire cho tác vụ nền.

---
Tài liệu này mang tính khái quát để onboard nhanh. Khi cần chi tiết hơn, đọc swagger và các service theo từng module.
