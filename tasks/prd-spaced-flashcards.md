# PRD · Thẻ ghi nhớ và ôn ngắt quãng

**Trạng thái:** Đã triển khai trong mã nguồn. Mở rộng G3.4 và luồng lưu gợi ý G6.

## 1. Hiện trạng và mục tiêu

Gemini đang trả từ hoặc mẫu câu, nghĩa tiếng Việt và ví dụ tiếng Anh. Backend đã sao chép nội dung này vào thẻ khi người học lưu gợi ý từ nhận xét thành công. Trang Ôn tập đã có lật thẻ và tự đánh dấu **Cần ôn / Đã nhớ**. Sổ tay có chi tiết thẻ và quiz chọn nghĩa. MySQL đã có `review_items.next_review_at`, nhưng luồng ôn hiện tại chưa dùng để xếp lịch.

Mục tiêu là đi trọn luồng **Nhận xét → Lưu thẻ → Lật và nghe → Ôn theo lịch → tải lại thấy lịch mới**. Lịch và kết quả đi theo tài khoản để dùng trên nhiều thiết bị. Thẻ đã lưu dùng được sau khi xóa hội thoại nguồn.

| Quyết định người dùng đã chốt | Áp dụng |
| --- | --- |
| Thẻ đã lưu trước đây | Tự đưa vào lịch, phân bổ cùng giới hạn thẻ mới. |
| Giọng phát âm | Dùng giọng Anh cục bộ của trình duyệt, tái dùng TTS G3. |
| Cách tự chấm | Bốn mức **Quên / Khó / Tốt / Dễ**. |

## 2. Câu chuyện người dùng

| Mã | Nhu cầu | Tiêu chí quan sát được |
| --- | --- | --- |
| US-01 | Tôi muốn lưu từ hoặc mẫu câu vừa được góp ý để dùng lại. | Bấm **Lưu thẻ** tạo một thẻ có đúng nghĩa và ví dụ. Bấm lại trả cùng ID. Link sau khi lưu mở đúng thẻ, kể cả khi thẻ đã tồn tại. |
| US-02 | Tôi muốn tự nhớ nghĩa rồi lật thẻ để tự kiểm tra và nghe phát âm. | Mặt trước hiện tiếng Anh. Mặt sau hiện nghĩa, ví dụ, IPA nếu có và nút nghe. Chuyển thẻ dừng phát cũ. Điều khiển dùng được bằng bàn phím. |
| US-03 | Tôi muốn biết thẻ nào đến hạn và lần sau nên ôn lúc nào. | **Ôn theo lịch** ưu tiên thẻ đến hạn. Sau khi lật, chọn một trong bốn mức và thấy hạn ôn mới. Tải lại hoặc chuyển thiết bị thấy cùng lịch. |
| US-04 | Tôi muốn các thẻ đã lưu sẵn tự vào lịch. | Thẻ cũ ở cả hai trạng thái tay xuất hiện trong hàng đợi chưa từng ôn theo lịch. Lịch sử cũ vẫn đọc được. Giới hạn thẻ mới áp dụng chung. |
| US-05 | Tôi muốn lần chấm đã gửi được giữ khi mất mạng hoặc mở hai tab. | Gửi lại cùng yêu cầu chỉ tính một lượt. Chấm cùng phiên bản thẻ từ hai thiết bị chỉ có một lượt được nhận. Tài khoản khác không đọc hoặc sửa thẻ. |

## 3. Yêu cầu chức năng

### FR-01 · Lưu thẻ từ nhận xét hội thoại

- Với nhận xét `SUCCEEDED`, mỗi gợi ý hiện từ/mẫu câu, nghĩa, ví dụ và nút **Lưu thẻ**. Hiện đang lưu, đã lưu và lỗi có thể thử lại. Sau lưu có link **Mở thẻ** theo ID trả về, dùng đường mở thẻ cá nhân hiện có.
- Backend kiểm tra quyền theo gợi ý → nhận xét → buổi luyện → chủ sở hữu. Sao chép loại thẻ, từ/mẫu câu, nghĩa và ví dụ. Trả thẻ đã tồn tại khi gửi lại cùng nguồn của cùng owner. Lưu hai gợi ý riêng biệt vẫn tạo hai thẻ dù trùng cách viết, vì chúng có thể khác nghĩa hoặc ngữ cảnh.
- Giữ nội dung đã xác thực trong snapshot. Nghĩa là bắt buộc theo hợp đồng hiện tại. Thẻ cũ thiếu ví dụ hiện **Chưa có ví dụ**. Chỉ hiện IPA nếu thẻ đã có phiên âm tin cậy. Nội dung AI hiển thị dưới dạng văn bản.
- Nút **Nghe phát âm** đọc từ/cụm từ đã lưu. **Nghe ví dụ** đọc câu ví dụ khi có. Dùng `useSpeech`, ưu tiên giọng Anh cục bộ `en-US` rồi đến giọng Anh cục bộ sẵn có. IPA dùng để tra cứu. Khi thiếu giọng hoặc phát lỗi, báo trạng thái và vẫn cho lật thẻ, chấm và xem lịch. Dừng phát khi lật về mặt trước, đổi thẻ, đổi chế độ hoặc rời trang. Bản đầu phát theo yêu cầu tại trình duyệt, không cần file, URL audio hoặc dịch vụ TTS phía server.

### FR-02 · Chế độ Lật thẻ

- Trang **Ôn bằng thẻ ghi nhớ** có hai lựa chọn **Lật thẻ** và **Ôn theo lịch**. Đề xuất mặc định mở **Lật thẻ**. Mặt trước chỉ hiện từ/mẫu câu tiếng Anh. **Xem mặt sau** hiện nghĩa, ví dụ, IPA nếu có và nút nghe. Người học có thể lật lại và chuyển thẻ kế tiếp.
- Duyệt toàn bộ thẻ hoặc lọc **Cần ôn / Đã nhớ**, có phân trang. Lật và phát chỉ là thao tác xem. Khi bấm tự đánh dấu, ghi lượt `FLASHCARD` theo hợp đồng hiện tại.
- Sổ tay tiếp tục hỗ trợ quiz chọn nghĩa. Lật thẻ, tự đánh dấu và làm quiz giữ nguyên lịch ngắt quãng. Thẻ có nhãn **Đã nhớ** vẫn xuất hiện trong chế độ ôn theo lịch khi đến hạn.

### FR-03 · Chế độ Ôn theo lịch

1. Khi mở chế độ, hiển thị số thẻ đến hạn, số thẻ chưa từng ôn theo lịch được phép học hôm nay và mốc đến hạn tiếp theo nếu có. **Bắt đầu ôn** lấy tối đa **20 thẻ** cho một đợt.
2. Xếp thẻ đã ôn có `next_review_at <= now` lên trước, theo hạn tăng dần. Sau đó lấy thẻ chưa từng ôn theo lịch theo `created_at` tăng dần. Hai mốc bằng nhau phân xử theo ID tăng dần. Đợt đang mở giữ thứ tự khi học viên chấm.
3. Đề xuất tối đa **10 thẻ chưa từng ôn theo lịch mỗi ngày** cho một tài khoản. Một thẻ dùng một suất khi chấm lần đầu thành công. Hết suất vẫn cho chấm thẻ cũ đến hạn. Backend kiểm lại hạn mức khi nhận kết quả. Giới hạn tính trên các thẻ còn tồn tại, nên xóa thẻ đã dùng suất có thể trả lại suất trong ngày đó.
4. Người học tự nhớ rồi lật. Mặt sau có **Quên** nếu không nhớ hoặc nhớ sai, **Khó** nếu nhớ đúng nhưng mất nhiều công, **Tốt** nếu nhớ đúng sau ít suy nghĩ, **Dễ** nếu nhớ ngay. Sau khi server xác nhận, hiển thị hạn vừa lưu và chuyển thẻ. **Bỏ qua** chỉ bỏ thẻ trong đợt hiện tại và giữ nguyên lịch.
5. Cuối đợt, hiển thị số thẻ đã chấm và cho **Tiếp tục ôn** nếu còn thẻ đủ điều kiện. Thẻ hẹn lại trong ngày có thể xuất hiện ở đợt sau khi tới hạn. Khi tải lại, lấy đợt mới từ lịch đã lưu. Trạng thái trống phân biệt chưa có thẻ, hết suất thẻ mới, và các thẻ còn lại chưa đến hạn.

### FR-04 · Thuật toán đặt lịch

Đề xuất `INTERVAL_V1`, lịch theo khoảng cách và bốn mức tự chấm. Gọi **I** là khoảng cách ôn đã lưu, tính bằng ngày. Thẻ mới hoặc vừa chấm **Quên** có I = 0. Backend tính từ một mốc UTC của chính lần ghi thành công. Dùng đồng hồ có thể cố định trong kiểm thử.

| Mức chấm | I = 0 | I > 0 |
| --- | --- | --- |
| **Quên** · `AGAIN` | Hẹn sau 10 phút, I = 0 | Hẹn sau 10 phút, đặt I = 0 |
| **Khó** · `HARD` | 1 ngày | Giá trị lớn hơn giữa I + 1 và làm tròn lên I × 1,2 |
| **Tốt** · `GOOD` | 3 ngày | Giá trị lớn hơn giữa I + 2 và làm tròn lên I × 2 |
| **Dễ** · `EASY` | 7 ngày | Giá trị lớn hơn giữa I + 3 và làm tròn lên I × 3 |

- Khoảng cách tối đa đề xuất **180 ngày**. Chấm trễ vẫn tính từ thời điểm chấm thực tế. Một ngày trong công thức là 24 giờ. Không cộng dồn số ngày người học đã bỏ lỡ.
- Mốc hẹn lưu UTC, hiển thị theo múi giờ lịch luyện G1, mặc định `Asia/Ho_Chi_Minh`. Suất thẻ mới tính theo ngày địa phương. Dùng hai mốc đầu ngày địa phương liên tiếp rồi đổi sang UTC để xử lý ngày đổi giờ mùa hè. Đổi múi giờ đổi cách hiển thị và ranh giới ngày tính suất, giữ nguyên mốc UTC đã hẹn.
- Ví dụ **Could you say that again?** có nghĩa *Bạn có thể nói lại được không?* và ví dụ *Sorry, I didn't catch that. Could you say that again?* Chấm **Tốt** lúc 09:00 ngày 27/11 thì hẹn 09:00 ngày 30/11 giờ Việt Nam. Chấm **Khó** đúng mốc đó thì I từ 3 lên 4 ngày và hẹn 09:00 ngày 04/12. Nếu lần kế tiếp chọn **Quên**, thẻ hẹn lại sau 10 phút. Chọn **Tốt** sau lần học lại khởi động khoảng 3 ngày.

### FR-05 · Database, thẻ cũ và phạm vi xóa

| Bảng | Thay đổi đề xuất |
| --- | --- |
| `review_items` | Tái dùng `next_review_at`. Thêm `spaced_interval_days` mặc định 0, `schedule_version` mặc định 0, `first_spaced_reviewed_at` nullable. Chỉ mục theo owner/hạn ôn và owner/mốc chấm lần đầu. Kiểm tra kế hoạch truy vấn với dữ liệu lớn. |
| `review_attempts` | Thêm `SPACED_REVIEW` vào CHECK theo mode. Thêm rating, phiên bản thuật toán, phiên bản lịch sau ghi, khoảng ôn được áp dụng và hạn ôn sau chấm. Các trường mới nullable cho lượt cũ. Với lượt ngắt quãng, `result` giữ nhãn tay ở thời điểm ghi, `is_correct` là NULL và rating lưu lựa chọn thực. |
| `feedback_suggestions` | Dùng schema và quy trình xác thực hiện tại. Copy nội dung qua nguồn chứ không truy vấn lại Gemini khi lưu. |

- Flyway mới dùng phiên bản kế tiếp còn trống. Giữ thẻ cũ ở phiên bản lịch 0 với mốc chấm đầu tiên trống. Thẻ cũ vào hàng đợi như thẻ chưa từng ôn theo lịch. Không suy hạn từ loại lượt cũ. Giữ nhãn tay, lịch sử và liên kết G7. Cả nguồn học liệu và nhận xét dùng cùng thuật toán.
- Xóa buổi hoặc gỡ học liệu chỉ gỡ liên kết nguồn, giữ snapshot và lịch. Xóa thẻ dọn lượt ôn qua FK. Xóa toàn bộ dữ liệu học tập hoặc xóa tài khoản dọn cả lịch và lịch sử ôn theo phạm vi. Request đến muộn sau xóa nhận 404. Giữ mô hình 46 bảng.

### FR-06 · API, đồng thời và mất mạng

| API dưới `/api/v1` | Hợp đồng đề xuất |
| --- | --- |
| `POST /learning/cards/from-suggestion/{id}` | Tái dùng API hiện tại, lấy ID thẻ từ phản hồi để mở thẻ. |
| `GET /learning/notebook/spaced?limit=20` | Trả `dueCount` cho thẻ đã chấm trước đây và đến hạn, `newEligibleCount` cho thẻ chưa chấm trong phần suất còn lại, `newRemainingToday`, `nextDueAt` cho mốc tương lai gần nhất, `nextLocalDayAt`, `timeZone` và `items`. Hai số đếm có thể lớn hơn số thẻ trả trong đợt. Giới hạn 1–20. Mỗi thẻ trả snapshot, hạn và `scheduleVersion`. |
| `POST /learning/notebook/{id}/attempts` | Mode `SPACED_REVIEW` nhận `rating`, `scheduleVersion` và `clientRequestId`. Ví dụ `{"mode":"SPACED_REVIEW","rating":"GOOD","scheduleVersion":0,"clientRequestId":"review-request-01"}`. Trả ID lượt, rating, hạn ôn mới và phiên bản lịch sau ghi. |
| `GET /learning/notebook/{id}/attempts` | Lịch sử phân trang gồm cả ba mode. Lượt ngắt quãng hiện mức chấm và hạn được áp lúc ghi. |

- Dùng Session, CSRF cho ghi và owner từ principal. Thẻ người khác trả 404. Từ chối payload trộn trường giữa các mode. Client gửi mức chấm và phiên bản đã đọc. Backend tự tính hạn.
- Trong một transaction, khóa người dùng theo quy ước hiện có để tuần tự hóa giới hạn theo owner. Sau khi kiểm tra thẻ, tra idempotency trước khi kiểm hạn và phiên bản. Nếu là lượt mới, ghi attempt và cập nhật lịch thẻ trong cùng giao dịch. Cùng mã và payload trả kết quả gốc dù thẻ đã được chấm lần khác. Cùng mã khác payload trả `409 REQUEST_ID_REUSED`.
- API dùng ba mã 409 để giao diện xử lý xung đột. `REVIEW_SCHEDULE_CHANGED` áp dụng khi phiên bản đã cũ, `REVIEW_NOT_DUE` khi chưa đến hạn và `NEW_REVIEW_LIMIT_REACHED` khi hết suất. UI làm mới đợt khi gặp xung đột. Trong lúc ghi, vô hiệu nút chấm. Mất mạng giữ thẻ và cho **Thử lại** với cùng request ID, rating và phiên bản.

## 4. Thiết kế giao diện và khả năng tiếp cận

- Mặt trước và mặt sau dùng nút HTML điều khiển được bằng Tab/Enter/Space. Nghĩa chưa mở cũng ẩn với trình đọc màn hình. Nội dung Anh có `lang="en"`. Sau khi chấm báo qua vùng trạng thái và chuyển focus tới thẻ mới.
- Kiểm tra desktop, viewport di động, giảm chuyển động và trình đọc màn hình. Có trạng thái tải, trống, lỗi mạng, thiếu giọng, hết phiên và thẻ đã bị xóa. Thiếu giọng vẫn hiển thị mặt sau và các nút chấm.

## 5. Kế hoạch và task

| Thứ tự | Task | Công việc | Điều kiện đạt | Ước lượng |
| --- | --- | --- | --- | --- |
| 1 | `SR-01` Hợp đồng và migration | Chốt trường, mốc giờ, lỗi, ràng buộc và chỉ mục. Chuẩn bị fixture cũ và mới. | MySQL rỗng và nâng cấp đều chạy. Thẻ, nhãn tay, quiz và lượt cũ giữ nguyên. | 3–5 giờ |
| 2 | `SR-02` Lưu và mở thẻ từ nhận xét | Rà quyền, snapshot và lưu trùng. Nối link mở thẻ theo ID, bổ sung trạng thái lưu/lỗi. | Một thẻ cho mỗi nguồn/owner. Xóa buổi vẫn mở được thẻ. | 2–3 giờ |
| 3 | `SR-03` Lật thẻ và TTS | Làm mặt trước/sau, nút nghe từ và ví dụ, dừng theo vòng đời. Giữ lọc, phân trang và đánh dấu tay. | Chrome kiểm hai mặt, bàn phím, đổi thẻ, lỗi hoặc thiếu giọng. Sổ tay và quiz chạy như trước. | 4–6 giờ |
| 4 | `SR-04` Tính lịch và hàng đợi | Viết bộ tính lịch với đồng hồ kiểm thử. Tạo truy vấn ưu tiên thẻ đến hạn và giới hạn theo ngày. | Bảng công thức, ngưỡng thời gian, thứ tự và suất qua nửa đêm có test. | 4–6 giờ |
| 5 | `SR-05` API chấm theo lịch | Nối DTO, xử lý mode, phiên bản, giao dịch, retry và lịch sử. | Hai tab chỉ ghi một lượt. Retry trả hạn cũ. Quyền chéo và xóa trả đúng lỗi. | 5–7 giờ |
| 6 | `SR-06` Giao diện ôn theo lịch | Thêm chế độ, đợt ôn, bốn mức, hạn ôn, lỗi xung đột và tổng kết đợt. | Duyệt toàn luồng trên Chrome desktop/mobile, kiểm cả cây trợ năng. Tải lại thấy lịch đã lưu. | 5–7 giờ |
| 7 | `SR-07` Hồi quy và bàn giao | Chạy unit, API–MySQL, build/typecheck/lint và Chromium. Rà từ và mẫu câu A2/B1. Đồng bộ API, ERD và hướng dẫn. | Ma trận bên dưới đạt hoặc có bằng chứng ghi rõ ca còn chờ. | 4–6 giờ |

Tổng đề xuất **27–40 giờ-người**. Sau khi chốt hợp đồng SR-01, phần lật thẻ có thể làm song song với phần tính lịch. SR-06 nghiệm thu sau khi SR-05 có API. SR-07 kiểm tra toàn luồng.

## 6. Ma trận kiểm thử và bàn giao

| Nhóm | Ca phải chạy | Kết quả cần đối chiếu |
| --- | --- | --- |
| Nguồn và quyền | Lưu từ, lưu mẫu câu, lưu đồng thời, gợi ý người khác, xóa hội thoại | Đúng nội dung snapshot, một thẻ trên mỗi nguồn và owner, owner khác nhận 404, thẻ mất nguồn vẫn dùng được. |
| Công thức | Bốn mức ở I=0 và I>0, hẹn 10 phút, trần 180 ngày, chấm trễ | Hạn UTC và phiên bản chính xác. Khoảng ôn tính từ lúc chấm. |
| Hàng đợi | Quá hạn, đúng hạn, tương lai, cùng ngày tạo, 11 thẻ mới, qua nửa đêm, đổi múi giờ | Thứ tự ổn định. Thẻ thứ 11 chờ ngày sau. Đổi múi giờ giữ hạn UTC. Hết suất mới vẫn ôn thẻ cũ đến hạn. |
| Đồng thời | Hai tab cùng phiên bản, gửi lại sau mất mạng, mã cũ với rating khác, xóa sau khi mở | Lịch chỉ tăng phiên bản một lần. Retry trả mốc đã ghi. Xung đột đúng mã. Thẻ xóa không được tái tạo. |
| Tương thích | Flyway từ rỗng và nâng cấp, thẻ cũ đã nhớ/chưa nhớ, quiz cũ, mục G7, xóa toàn bộ | Lượt cũ và liên kết đọc được. Lật/quiz giữ lịch. Xóa dọn lịch và các attempt theo phạm vi. |
| UI và TTS | Hai chế độ, bàn phím, cây trợ năng, màn hình nhỏ, nghe từ và ví dụ, đổi thẻ/rời trang, thiếu giọng, mất phiên | Đáp án ẩn tới khi lật. Giọng đọc đúng phần được chọn và dừng khi chuyển. Có thể tiếp tục bằng văn bản khi thiếu giọng. |

Theo dõi tỷ lệ đi trọn luồng lưu đến chấm lần đầu, số lượt chấm theo mức và tỷ lệ hoàn thành đợt. Những số này đo hoạt động ôn tập. Đánh giá mức tăng ghi nhớ dài hạn cần nghiên cứu riêng.

### Kết quả kiểm chứng

- MySQL 9.2 dựng được từ rỗng đến V26. Từ V25 có dữ liệu flashcard và quiz, V26 giữ thẻ và lịch sử cũ, chạy lần hai không áp lại.
- Hai ca unit công thức và hai ca API–MySQL đạt. Các ca bao gồm chấm trùng từ hai phiên, thử lại sau khi đã chấm lần khác, đổi múi giờ, mốc đầu ngày, hạn mức thẻ mới, quyền truy cập, xóa và luồng lưu nhận xét giả lập.
- Chrome headless ở viewport di động đi từ nhận xét đến đúng thẻ, lật bằng bàn phím, gọi phát âm và ví dụ, xử lý thiếu giọng, thử lại cùng payload và kiểm lịch sau tải lại. Giọng đọc dùng fixture. Chưa xác minh âm thanh thật trên thiết bị di động hoặc thử thủ công với trình đọc màn hình.
- Frontend qua typecheck, lint và build. Vite vẫn báo cảnh báo kích thước bundle chính.

## 7. Phạm vi và mặc định còn điều chỉnh được

- Bản đầu tập trung vào thẻ cá nhân, lịch theo khoảng cách và tự đánh giá. Thuật toán cá nhân hóa, nhắc ôn riêng, đồng bộ offline, audio tải xuống, tạo IPA bằng AI và sửa nội dung thẻ dành cho đợt sau.
- Ba quyết định đã chốt nằm ở mục 1. Các giá trị đề xuất cho triển khai đầu gồm 10 thẻ mới/ngày, 20 thẻ/đợt, hẹn lại sau 10 phút, trần 180 ngày và mở mặc định ở Lật thẻ.

[Kế hoạch hệ thống G1–G10](../docs/development/full-system-implementation-plan.md) · [API học liệu và sổ tay](../docs/api/lesson-materials.md) · [API lưu gợi ý](../docs/api/learning.md) · [Đặc tả dữ liệu hiện tại](../docs/architecture/database.md) · [Hướng dẫn kiểm thử](../docs/development/testing.md).
