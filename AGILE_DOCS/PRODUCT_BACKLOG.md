# Product Backlog

## Muc tieu san pham
- Tu dong capture frame chat luong tot.
- Do kich thuoc tay on dinh dua tren the ID-1 va hand landmarks.
- Van hanh tot tren dien thoai Android that.

## Uu tien backlog (cao -> thap)

| ID | User Story | Gia tri | SP | Priority | Ghi chu |
|---|---|---|---:|---|---|
| PBI-01 | Tich hop MediaPipe hand tracker cho quality gate de bo fake tracker | Tang do tin cay gating | 5 | High | Da lam |
| PBI-02 | Card detection gate trong quality pipeline de chan capture khi khong thay the | Giam frame xau | 5 | High | Da lam |
| PBI-03 | Overlay huong dan vi tri tay + the (net dut) tren preview | Giam sai tu nguoi dung | 3 | High | Da lam |
| PBI-04 | Tinh size tu topK va hien thi man hinh ket qua rieng | Hoan thanh flow E2E | 5 | High | Da lam |
| PBI-05 | Upload API chi khi debug bat | Ho tro debug va trace | 3 | Medium | Da lam (stub endpoint) |
| PBI-06 | Calibration goc nghieng the + canh bao do lech mat phang | Tang do chinh xac mm | 5 | Medium | Chua lam |
| PBI-07 | Dashboard debug card confidence / hand confidence realtime | De tuning threshold | 3 | Medium | Chua lam |
| PBI-08 | E2E instrumented test tren may that (camera flow) | Giam regression runtime | 8 | Medium | Chua lam |
| PBI-09 | Retry queue uploader + backoff + persistence | Tang do ben upload | 5 | Low | Chua lam |
| PBI-10 | Chuan hoa i18n strings.xml cho toan bo UI text | De maintain UI | 3 | Low | Chua lam |

## Nguong ky thuat hien tai
- Gate quality:
- `readyThreshold=0.65`
- `stableThreshold=0.78`
- `stableFrames=12`
- `captureDurationMs=1500`
- `cooldownMs=1000`
- `topK=10`
- Card gate:
- `requireCardForCapture=true`
- `cardMinConfidence=0.75`
- `cardAnalysisIntervalMs=180`

