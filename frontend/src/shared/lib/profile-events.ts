// Thông báo nội bộ sau khi server đã lưu hồ sơ/ảnh; không chứa dữ liệu cá nhân.
export const profileChangedEvent = 'learning-profile-changed'
export function notifyProfileChanged() {
  window.dispatchEvent(new Event(profileChangedEvent))
}
