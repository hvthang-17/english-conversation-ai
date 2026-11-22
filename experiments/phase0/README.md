# Thử nghiệm giai đoạn 0

Thí nghiệm độc lập với runtime ứng dụng. Không triển khai AUTH-01 hoặc gọi Gemini thật trong bước này.

- audio/: trang kiểm tra codec/micro local, không upload, người dùng tự cấp quyền và nghe lại.
- gemini/: Maven Java 21 probe REST với unit tests/dry-run, wrapper hiện chặn Live.
- fixtures/: 32 tình huống tổng hợp cho đánh giá sau, không phải kết quả đã chạy hoặc gold set chuyên gia.

Hướng dẫn/bằng chứng tại [phase0](../../docs/development/phase0/README.md), [MySQL](../../docs/development/phase0/mysql.md), [Gemini/key](../../docs/development/phase0/gemini.md), [audio](../../docs/development/phase0/audio.md).

Secrets, bản ghi và kết quả local nằm ở .env/.runtime bị ignore. Không commit raw output provider/âm thanh cá nhân hoặc log chưa kiểm tra. Mock/dry-run không chứng minh chất lượng model, hiệu năng hoặc chi phí thật.
