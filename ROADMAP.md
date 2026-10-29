# Lộ Trình Triển Khai 4 Module Nghiệp Vụ Bàn Giao Cho FE & Mobile Dev

Tài liệu này thuyết minh và hướng dẫn chi tiết theo đúng lộ trình tuần tự:
**Cart (Giỏ hàng) ➔ Profile & Address (Hồ sơ & Sổ địa chỉ) ➔ Checkout & Order (Đặt hàng & Xử lý đơn) ➔ Search & Advanced Filter (Tìm kiếm & Bộ lọc nâng cao)**.

Toàn bộ mã nguồn thực thi đã được tích hợp đầy đủ vào gói dự án `ecommerce-backend.zip`.

---

## Bước 1: Module Cart (Quản Lý Giỏ Hàng)

### 1.1. Mục tiêu & Luồng hoạt động cho FE/Mobile
- Giỏ hàng gắn liền với từng User qua `userId` (`GET /api/v1/cart`).
- Cho phép thêm sản phẩm vào giỏ (`POST /api/v1/cart/items`), tăng/giảm số lượng (`PUT /api/v1/cart/items`), xóa từng món (`DELETE /api/v1/cart/items/{productId}`) hoặc làm trống giỏ (`DELETE /api/v1/cart/clear`).
- **Xử lý nghiệp vụ**:
  - Tự động kiểm tra số lượng tồn kho (`stockQuantity`) trước khi cho phép thêm hoặc tăng số lượng; nếu thiếu, hệ thống ném mã lỗi `3004 - INSUFFICIENT_STOCK`.
  - Tự động cập nhật lại đơn giá (`unitPrice`) mới nhất từ bảng Product và tính tổng tiền (`totalAmount`) theo thời gian thực.

### 1.2. Danh sách Endpoints
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/cart` | Authenticated | Lấy chi tiết giỏ hàng và tổng tiền |
| `POST` | `/api/v1/cart/items` | Authenticated | Thêm sản phẩm vào giỏ hàng |
| `PUT` | `/api/v1/cart/items` | Authenticated | Cập nhật số lượng sản phẩm trong giỏ |
| `DELETE` | `/api/v1/cart/items/{productId}` | Authenticated | Xóa 1 món hàng khỏi giỏ |
| `DELETE` | `/api/v1/cart/clear` | Authenticated | Xóa toàn bộ giỏ hàng |

---

## Bước 2: Module Profile & Address Book (Hồ Sơ & Sổ Địa Chỉ Giao Hàng)

### 2.1. Mục tiêu & Luồng hoạt động cho FE/Mobile
- Mobile App cần cho phép khách hàng lưu nhiều địa chỉ nhận hàng (ví dụ: Nhà riêng, Công ty) và chọn 1 địa chỉ mặc định (`isDefault: true`).
- Hỗ trợ đổi mật khẩu an toàn (`POST /api/v1/profile/change-password`): Yêu cầu mật khẩu cũ khớp với BCrypt hash trước khi cho phép cập nhật.

### 2.2. Danh sách Endpoints
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/profile/me` | Authenticated | Lấy thông tin cá nhân và danh sách địa chỉ |
| `PUT` | `/api/v1/profile/me` | Authenticated | Cập nhật họ tên, số điện thoại, avatar URL |
| `POST` | `/api/v1/profile/change-password` | Authenticated | Đổi mật khẩu tài khoản |
| `GET` | `/api/v1/profile/addresses` | Authenticated | Xem danh sách các địa chỉ nhận hàng đã lưu |
| `POST` | `/api/v1/profile/addresses` | Authenticated | Thêm mới địa chỉ nhận hàng |
| `PUT` | `/api/v1/profile/addresses/{addressId}` | Authenticated | Chỉnh sửa thông tin địa chỉ |
| `DELETE` | `/api/v1/profile/addresses/{addressId}` | Authenticated | Xóa địa chỉ |
| `PUT` | `/api/v1/profile/addresses/{addressId}/default` | Authenticated | Đặt làm địa chỉ nhận hàng mặc định |

---

## Bước 3: Module Checkout & Order (Đặt Hàng & Xử Lý Đơn Hàng)

### 3.1. Mục tiêu & Vòng đời đơn hàng (State Machine)
- **Tạo đơn hàng (`POST /api/v1/orders`)**:
  - Khách hàng chỉ cần truyền `addressId`, `paymentMethod` (COD, VNPAY, MOMO) và ghi chú `note`.
  - Hệ thống lấy danh sách món từ Giỏ hàng của User, kiểm tra tồn kho lần cuối, **trừ trực tiếp tồn kho của từng sản phẩm trong MongoDB**, snapshot lại giá và thông tin người nhận tại thời điểm đặt, tạo mã đơn hàng duy nhất dạng `ORD-{timestamp}-{uuid}`, và dọn sạch giỏ hàng.
- **Hủy đơn hàng (`PUT /api/v1/orders/{id}/cancel`)**:
  - Khách hàng chỉ được phép hủy khi đơn ở trạng thái `PENDING`.
  - Khi hủy thành công, hệ thống **tự động hoàn trả lại số lượng tồn kho** cho các sản phẩm.
- **Vòng đời chuyển trạng thái (Dành cho Admin/Chủ shop)**:
  `PENDING` (Chờ duyệt) ➔ `CONFIRMED` (Đã xác nhận) ➔ `SHIPPING` (Đang giao) ➔ `DELIVERED` (Đã giao thành công).

### 3.2. Danh sách Endpoints
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/orders` | Customer | Đặt hàng từ giỏ hàng hiện tại |
| `GET` | `/api/v1/orders` | Customer | Xem lịch sử đơn hàng của bản thân (phân trang) |
| `GET` | `/api/v1/orders/{id}` | Customer | Xem chi tiết 1 đơn hàng |
| `PUT` | `/api/v1/orders/{id}/cancel` | Customer | Hủy đơn hàng (khi đang PENDING) |
| `GET` | `/api/v1/admin/orders` | Admin | Quản trị viên xem toàn bộ đơn hàng (lọc theo status) |
| `PUT` | `/api/v1/admin/orders/{id}/status` | Admin | Quản trị viên cập nhật trạng thái đơn hàng |

---

## Bước 4: Module Search & Advanced Filter (Tìm Kiếm & Bộ Lọc Nâng Cao)

### 4.1. Mục tiêu & Tối ưu cho Mobile / Frontend
- Hỗ trợ thanh tìm kiếm toàn diện (`keyword`): Tìm kiếm không phân biệt hoa thường (Regex Case-insensitive) trên cả `name`, `description` và mã `sku`.
- Kết hợp đa điều kiện:
  - Lọc theo danh mục: `categoryId`.
  - Lọc theo khoảng giá: `minPrice` và `maxPrice`.
  - Sắp xếp linh hoạt: `sortBy` (`createdAt`, `price`, `name`), `sortDir` (`asc`, `desc`).
- Phân trang chuẩn mực: Trả về `PageResponse<T>` gồm `pageNo`, `pageSize`, `totalElements`, `totalPages` và `last` (hỗ trợ Mobile làm Infinite Scroll mượt mà).

### 4.2. Danh sách Endpoints
| Phương thức | Đường dẫn API | Quyền truy cập | Tham số Query hỗ trợ |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/public/products/search` | Public | `keyword`, `categoryId`, `minPrice`, `maxPrice`, `page`, `size`, `sortBy`, `sortDir` |
