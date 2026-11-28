# ADR 0020 — Module review sở hữu thẻ và lượt ôn

Ngày: 28/11/2026. Trạng thái: chấp nhận. Phạm vi: BR-08, REVIEW-01 và phần thẻ của HISTORY-01.

## Bối cảnh

LearningStore và NotebookRepository cùng ghi review_items/review_attempts. Hai đường FLASHCARD chống trùng khác nhau: cards chỉ so result, notebook so mode và fingerprint. Do đó key của QUICK_QUIZ hoặc SPACED_REVIEW có thể bị cards nhận nhầm là một FLASHCARD đã ghi. Việc xóa dữ liệu học và tài khoản còn đi qua hai nơi xóa thẻ.

## Quyết định

- `review/repository/ReviewRepository` là nơi thực hiện insert/update/delete thẻ và lượt ôn. Hai nguồn dùng một Snapshot có kiểu; nguồn suggestion được đọc bởi FeedbackStore, nguồn học liệu bởi LessonMaterialsRepository. FK/provenance, IPA, nghĩa và ví dụ tiếp tục được sao chép theo từng nguồn.
- `review/service/ReviewService` sở hữu lưu thẻ, kiểm tra owner, replay, FLASHCARD, SPACED_REVIEW, hàng đợi và giới hạn thẻ mới. SpacedReviewSchedule nằm cùng module. Khóa tài khoản được giữ trong transaction trước đọc/ghi thẻ để hai API dùng chung thứ tự khóa. Cleanup tài khoản gọi ReviewRepository khi caller đã giữ khóa tài khoản và transaction hiện có.
- LearningService và NotebookService là adapter cho hai hợp đồng HTTP hiện có; ReviewViews chuyển dữ liệu miền ReviewData sang DTO cũ. ReviewData không phụ thuộc DTO learning. Lịch sử/thống kê/kế hoạch/gợi ý ngoài phần ghi thẻ tiếp tục theo phạm vi BR-09.
- Phạm vi clientRequestId vẫn là một thẻ. Payload FLASHCARD được chuẩn hóa thành cùng fingerprint ở hai adapter. Nếu hàng cũ thiếu fingerprint, chỉ chấp nhận replay khi mode gốc là FLASHCARD và result trùng; không sửa hàng cũ trong lúc replay. QUICK_QUIZ vẫn đọc được ở mapper/lưu trữ, không nhận lượt mới. Mode khác hoặc payload khác cùng key trả REQUEST_ID_REUSED (409).
- FLASHCARD chỉ đổi trạng thái và mốc ôn, không tự khởi tạo hoặc đổi lịch spaced. Chỉ yêu cầu SPACED_REVIEW hợp lệ mới ghi version, interval và due date. Replay trả attempt cũ trước kiểm tra hạn/version hiện tại.

## Hệ quả

Không thêm dependency hoặc migration, không backfill fingerprint, không đổi dữ liệu QUICK_QUIZ, không đổi JSON/validation của hai API. Giữ thứ tự danh sách cards theo id và notebook theo created_at/id, cùng giới hạn phân trang cũ. Xóa nguồn giữ bản sao qua FK SET NULL; xóa thẻ dọn attempts qua FK CASCADE hiện có. SQL đọc tổng hợp liên quan thẻ ở các module khác chưa được gom chỉ vì chúng đọc cùng bảng; mọi đường ghi thuộc review.

Bằng chứng và trạng thái nghiệm thu nằm tại [BR-08](../development/backend-refactoring/evidence/br08-review-ownership.md).
