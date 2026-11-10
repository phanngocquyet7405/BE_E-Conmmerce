# TÀI LIỆU ĐẶC TẢ HỢP ĐỒNG API: PHÂN HỆ SỔ ĐỊA CHỈ (ADDRESS BOOK API CONTRACT)

> **Dự án**: BE_E-Commerce (Spring Boot 3.3.4, Java 21 LTS, MongoDB, Redis)  
> **Phiên bản API**: `v1`  
> **Base Path**: `/api/v1/profile/addresses`  
> **Đối tượng sử dụng**: Android Mobile Engineer & Next.js Frontend Engineer  
> **Ngày ban hành**: 2026-11-10  

---

## 1. TỔNG QUAN & NGUYÊN TẮC BẤT BIẾN (INVARIANTS)

Hệ thống quản lý sổ địa chỉ giao hàng nhúng trực tiếp trong hồ sơ người dùng (`User.addresses`), phục vụ hiển thị danh sách nhận hàng trên ứng dụng di động và snapshot vào đơn hàng khi Checkout.

### 1.1. Bất biến về Địa chỉ Mặc định (Default Address Invariant)
1. **Duy nhất một địa chỉ mặc định**: Với mọi người dùng có ít nhất một địa chỉ, danh sách địa chỉ luôn có **ĐÚNG MỘT** địa chỉ mang cờ `"isDefault": true`.
2. **Tự động gán mặc định cho địa chỉ đầu tiên**: Khi tài khoản chưa có địa chỉ nào, địa chỉ thêm mới đầu tiên luôn tự động được gán `"isDefault": true` (dù request gửi `false`).
3. **Chuyển đổi mặc định nguyên tử**: Khi thêm mới hoặc cập nhật một địa chỉ với `"isDefault": true`, địa chỉ mặc định trước đó tự động chuyển thành `false`.
4. **Không cho phép bỏ mặc định trên địa chỉ duy nhất**: Khi người dùng chỉ có 1 địa chỉ duy nhất và gửi yêu cầu sửa với `"isDefault": false`, hệ thống vẫn bảo lưu giá trị `true` để bảo đảm bất biến.
5. **Tự động thăng hạng khi xóa địa chỉ mặc định**: Khi người dùng xóa địa chỉ đang là mặc định mà vẫn còn các địa chỉ khác, địa chỉ đầu tiên còn lại trong danh sách sẽ tự động được thăng hạng làm mặc định (`"isDefault": true`).

### 1.2. Giới hạn số lượng địa chỉ (Capacity Hard Cap)
- Mỗi tài khoản người dùng được lưu trữ tối đa **10 địa chỉ** (cấu hình qua biến môi trường `app.address.max-per-user`).
- Nếu cố gắng thêm địa chỉ thứ 11, hệ thống từ chối với HTTP 400 và mã lỗi `ErrorCode.ADDRESS_LIMIT_EXCEEDED` (mã `4002`).

### 1.3. Khóa JSON `isDefault` & Tương thích ngược (Backward Compatibility)
- **Chiều Request (Client gửi lên)**: Backend chấp nhận linh hoạt cả 3 định dạng khóa:
  - `"isDefault": true` (Khuyên dùng cho Mobile/FE chuẩn)
  - `"default": true` (Hỗ trợ định dạng cũ)
  - `"is_default": true` (Hỗ trợ định dạng snake_case)
- **Chiều Response (Backend trả về)**: Backend luôn trả về duy nhất khóa `"isDefault": true|false` đồng nhất trên toàn bộ các endpoint (Address list, UserProfile, AdminUser, OrderResponse).

### 1.4. Tính độc lập của Đơn hàng (Order Checkout Deep Snapshot)
- Khi khách hàng đặt hàng (`POST /api/v1/orders`), hệ thống tạo một **bản sao sâu độc lập (deep snapshot)** của địa chỉ giao hàng được chọn và lưu vào đơn hàng (`Order.shippingAddress`).
- Mọi thao tác sửa đổi hoặc xóa địa chỉ sau này trong sổ địa chỉ cá nhân **hoàn toàn không làm biến đổi dữ liệu địa chỉ trên các đơn hàng đã đặt trong quá khứ**.

---

## 2. BẢNG ĐẶC TẢ CÁC TRƯỜNG DỮ LIỆU (`AddressRequest` & `Address`)

| Trường dữ liệu | Kiểu dữ liệu | Bắt buộc | Ràng buộc Validation & Quy tắc chuẩn hóa | Mô tả |
| :--- | :---: | :---: | :--- | :--- |
| `id` | `String` | Không (Chỉ có ở Response) | UUIDv4 do Backend sinh tự động | Định danh duy nhất của địa chỉ |
| `recipientName` | `String` | **Có** | Độ dài 2 - 100 ký tự; không để trống; tự động `trim()` | Họ và tên người nhận hàng |
| `phone` | `String` | **Có** | Định dạng di động Việt Nam: `^(0\|\+84)(3\|5\|7\|8\|9)\d{8}$`; tự động `trim()` | Số điện thoại liên hệ nhận hàng |
| `province` | `String` | **Có** | Tối đa 100 ký tự; không để trống; tự động `trim()` | Tỉnh / Thành phố |
| `district` | `String` | **Không** | Tối đa 100 ký tự; cho phép null hoặc chuỗi rỗng (chuỗi rỗng tự động lưu `null`) | Quận / Huyện / Thị xã (hỗ trợ mô hình 2 cấp) |
| `ward` | `String` | **Có** | Tối đa 100 ký tự; không để trống; tự động `trim()` | Phường / Xã / Thị trấn |
| `streetAddress` | `String` | **Có** | Tối đa 255 ký tự; không để trống; tự động `trim()` | Số nhà, tên đường, thôn xóm, khu phố |
| `label` | `String` (Enum) | **Không** | Chỉ chấp nhận: `HOME`, `OFFICE`, `OTHER` (sai enum trả HTTP 400 `MALFORMED_JSON`) | Nhãn phân loại địa chỉ |
| `note` | `String` | **Không** | Tối đa 200 ký tự; tự động `trim()`; chuỗi rỗng tự động lưu `null` | Ghi chú giao hàng (vd: "Gọi trước 15p", "Gửi bảo vệ") |
| `latitude` | `Double` | **Không** | Khoảng `[-90.0, 90.0]`; hữu hạn (`Double.isFinite`); **bắt buộc đi kèm `longitude`** | Vĩ độ GPS phục vụ bản đồ mobile |
| `longitude` | `Double` | **Không** | Khoảng `[-180.0, 180.0]`; hữu hạn (`Double.isFinite`); **bắt buộc đi kèm `latitude`** | Kinh độ GPS phục vụ bản đồ mobile |
| `provinceCode` | `String` | **Không** | Tối đa 20 ký tự; tự động `trim()` | Mã hành chính Tỉnh/Thành phố (chuẩn Tổng cục Thống kê) |
| `districtCode` | `String` | **Không** | Tối đa 20 ký tự; tự động `trim()` | Mã hành chính Quận/Huyện |
| `wardCode` | `String` | **Không** | Tối đa 20 ký tự; tự động `trim()` | Mã hành chính Phường/Xã |
| `isDefault` | `Boolean` | **Không** | Nhận `isDefault` / `default` / `is_default`; mặc định `false` nếu không gửi | Cờ đánh dấu địa chỉ mặc định |

---

## 3. DANH SÁCH ENDPOINTS CHI TIẾT

### 3.1. Lấy danh sách địa chỉ nhận hàng
- **Phương thức**: `GET`
- **Đường dẫn**: `/api/v1/profile/addresses`
- **Yêu cầu xác thực**: Bắt buộc Header `Authorization: Bearer <accessToken>`
- **Phản hồi thành công (HTTP 200)**:
```json
{
  "code": 1000,
  "message": "Thành công",
  "data": [
    {
      "id": "7a3f81e2-9b4c-4e8a-bf92-6d2c4e1a8b90",
      "recipientName": "Nguyễn Văn A",
      "phone": "0987654321",
      "province": "TP Hồ Chí Minh",
      "district": "Quận 1",
      "ward": "Phường Bến Nghé",
      "streetAddress": "72 Lê Thánh Tôn",
      "label": "OFFICE",
      "note": "Tòa nhà Vincom Center lầu 12",
      "latitude": 10.7769,
      "longitude": 106.7009,
      "provinceCode": "79",
      "districtCode": "760",
      "wardCode": "26734",
      "isDefault": true
    },
    {
      "id": "e2b9c8d1-5a3f-4e8a-9b4c-1a8b906d2c4e",
      "recipientName": "Nguyễn Văn A",
      "phone": "0987654321",
      "province": "Bình Dương",
      "district": null,
      "ward": "Phường Phú Hòa",
      "streetAddress": "Số 45 Đại lộ Bình Dương",
      "label": "HOME",
      "note": null,
      "latitude": null,
      "longitude": null,
      "provinceCode": "74",
      "districtCode": null,
      "wardCode": "25750",
      "isDefault": false
    }
  ]
}
```

---

### 3.2. Thêm mới địa chỉ nhận hàng
- **Phương thức**: `POST`
- **Đường dẫn**: `/api/v1/profile/addresses`
- **Yêu cầu xác thực**: Bắt buộc Header `Authorization: Bearer <accessToken>`
- **Request Body (Đầy đủ các trường)**:
```json
{
  "recipientName": "Nguyễn Văn A",
  "phone": "0987654321",
  "province": "TP Hồ Chí Minh",
  "district": "Quận 1",
  "ward": "Phường Bến Nghé",
  "streetAddress": "72 Lê Thánh Tôn",
  "label": "OFFICE",
  "note": "Giao giờ hành chính, gọi trước 10 phút",
  "latitude": 10.7769,
  "longitude": 106.7009,
  "provinceCode": "79",
  "districtCode": "760",
  "wardCode": "26734",
  "isDefault": true
}
```
- **Request Body (Tối giản - Không có GPS, District, Nhãn)**:
```json
{
  "recipientName": "Trần Thị B",
  "phone": "+84912345678",
  "province": "Hà Nội",
  "ward": "Phường Hàng Bạc",
  "streetAddress": "12 Hàng Bạc",
  "isDefault": false
}
```
- **Phản hồi thành công (HTTP 200)**: Trả về đối tượng `Address` vừa tạo kèm `id` và cờ `isDefault` đã chuẩn hóa.

---

### 3.3. Cập nhật địa chỉ nhận hàng
- **Phương thức**: `PUT`
- **Đường dẫn**: `/api/v1/profile/addresses/{addressId}`
- **Yêu cầu xác thực**: Bắt buộc Header `Authorization: Bearer <accessToken>`
- **Quy tắc cập nhật**:
  - Ghi đè toàn bộ thông tin địa chỉ với các trường gửi lên trong body.
  - Các trường chuỗi được tự động `trim()`.
  - Nếu client truyền trường `note`, `district`, `provinceCode`... là chuỗi rỗng hoặc `null`, Backend sẽ cập nhật trường đó thành `null` trong cơ sở dữ liệu.
- **Phản hồi thành công (HTTP 200)**: Trả về đối tượng `Address` sau khi cập nhật.

---

### 3.4. Xóa địa chỉ nhận hàng
- **Phương thức**: `DELETE`
- **Đường dẫn**: `/api/v1/profile/addresses/{addressId}`
- **Yêu cầu xác thực**: Bắt buộc Header `Authorization: Bearer <accessToken>`
- **Xử lý đặc biệt**:
  - Nếu `addressId` không tồn tại trong danh sách của người dùng: Trả lỗi HTTP 404 (`ADDRESS_NOT_FOUND`).
  - Nếu xóa địa chỉ đang là mặc định: Tự động chọn địa chỉ đầu tiên còn lại làm mặc định.
- **Phản hồi thành công (HTTP 200)**:
```json
{
  "code": 1000,
  "message": "Thành công"
}
```

---

### 3.5. Đặt địa chỉ nhận hàng làm mặc định
- **Phương thức**: `PUT`
- **Đường dẫn**: `/api/v1/profile/addresses/{addressId}/default`
- **Yêu cầu xác thực**: Bắt buộc Header `Authorization: Bearer <accessToken>`
- **Hành vi**: Đặt địa chỉ mục tiêu mang `"isDefault": true`, đồng thời gán `"isDefault": false` cho tất cả các địa chỉ khác của người dùng.
- **Phản hồi thành công (HTTP 200)**:
```json
{
  "code": 1000,
  "message": "Thành công"
}
```

---

## 4. BẢNG MÃ LỖI THƯỜNG GẶP (ERROR CODES)

| Mã lỗi (`code`) | Tên enum (`error`) | HTTP Status | Nguyên nhân & Ngữ cảnh |
| :---: | :--- | :---: | :--- |
| `1001` | `UNAUTHENTICATED` | 401 | Thiếu Header Authorization hoặc Access Token đã hết hạn / không hợp lệ. |
| `2001` | `VALIDATION_ERROR` | 400 | Vi phạm validation dữ liệu: Tên người nhận không đủ 2 ký tự, số điện thoại không đúng chuẩn di động Việt Nam, tọa độ GPS vi phạm (chỉ truyền lat mà thiếu lng, hoặc giá trị ngoài phạm vi `[-90, 90]`, `[-180, 180]`). |
| `2002` | `MALFORMED_JSON` | 400 | Sai cú pháp JSON gửi lên hoặc gửi giá trị `label` không nằm trong enum (`HOME`, `OFFICE`, `OTHER`). |
| `4001` | `ADDRESS_NOT_FOUND` | 404 | Không tìm thấy `addressId` trong sổ địa chỉ của người dùng khi Sửa, Xóa, Đặt mặc định, hoặc khi Checkout đơn hàng. |
| `4002` | `ADDRESS_LIMIT_EXCEEDED` | 400 | Người dùng đã đạt tối đa 10 địa chỉ nhận hàng, không thể thêm mới. |
| `4005` | `METHOD_NOT_ALLOWED` | 405 | Gọi sai phương thức HTTP trên endpoint (ví dụ `POST` vào `/addresses/{id}`). |
