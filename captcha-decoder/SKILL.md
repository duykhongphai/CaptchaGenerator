---
name: captcha-decoder
description: >-
  Decode and verify the local 128x128 multi-layer overlapping CAPTCHA family
  whose 5-6 colored top glyphs are mapped to digits by five rules in the lower
  half and whose only special characters are #, $, %, &, @. Use for visual
  decoding, label review, and automatic schema-safe JSON upserts for this CAPTCHA
  family; do not apply these font rules to unrelated CAPTCHAs.
---

# Giải mã CAPTCHA đa lớp chồng lấp

## 1. Phạm vi và nguồn bằng chứng

Skill này áp dụng cho họ ảnh trong thư mục mẫu
C:\Users\nguye\Downloads\images_bgonly:

- Ảnh 128x128, nền xám, chữ và nhiễu dùng các màu thuần.
- Dải trên chứa chuỗi 5 hoặc 6 glyph chồng lấp.
- Nửa dưới chứa đúng 5 luật dạng ký_tự -> chữ_số.
- Ký tự trong chuỗi trên có thể lặp lại; một hoặc nhiều key của bảng luật có
  thể không xuất hiện trong chuỗi trên.

Kết quả audit ngày 2026-08-26:

- test_labels.json có 704 record, gồm nhiều dự đoán AI chưa review.
- review_labels.json có 354 record chuẩn hóa do người dùng xác nhận; tất cả nằm
  trong test_labels.json nhưng chỉ 109 record trùng hoàn toàn. Có 245 record
  lệch ít nhất một field, trong đó 243 lệch raw_top_string và 33 lệch rules.
- 154 record test bị validator hiện tại loại ngay; trong 200 record vượt
  veto vẫn còn 91 sai. Vì vậy test-only record không được dùng làm exemplar.
- Số liệu là snapshot. Luôn đọc lại hai file; xem phân tích chi tiết tại
  [references/review-calibration.md](references/review-calibration.md).

Tập ký tự:

- Ký tự đặc biệt hợp lệ chỉ có đúng năm ký tự: #, $, %, &, @. Không có bất kỳ
  special nào khác ở top hoặc rules.
- Review top có đủ chữ hoa A, B, C, D, E, F, H, K, L, N, P, Q, S, T, U, V, W,
  X, Z và nhiều dạng thường; d thường, M/m và R/r chưa có top exemplar review.
- Người dùng xác nhận M/m và R/r hợp lệ nhưng hiếm; review có một rule key r
  không dùng ở top.
- Không xuất G/g, I/i, J/j, O/o hoặc Y/y trong raw_top_string. Tuy nhiên review
  xác nhận i, o và y có thể xuất hiện như rule distractor không dùng ở top. Đọc
  và lưu đủ các rule này nhưng loại chúng khỏi top candidate set. G/g và J/j
  hiện không có ở cả top lẫn rules.
- Bất kỳ nhận dạng nào tạo special ngoài #, $, %, &, @ đều không hợp lệ và
  không được sao chép vào output.

Thứ tự ưu tiên khi có mâu thuẫn:

1. Sửa tay hoặc ground truth mới do người dùng cung cấp.
2. Record trong review_labels.json.
3. Record test-only chỉ dùng như dự đoán cần kiểm tra, không dùng để học font.
4. Suy luận trực quan từ ảnh.

Không dùng filename ngẫu nhiên để phát minh quy tắc glyph. Khi rút kinh nghiệm
mới, chọn ảnh bằng raw_top_string hoặc rules trong file nhãn trước, rồi mới mở
đúng filename và đối chiếu lớp màu.

Ngân hàng mẫu có nhãn nằm tại
[references/labeled-exemplars.md](references/labeled-exemplars.md).

- Khi decode batch hoặc sửa lỗi nhận dạng, đọc thêm
  [references/review-calibration.md](references/review-calibration.md).
- Khi decode ảnh mới/chưa nhãn, phải đọc chỉ mục này trước khi phát hành kết
  quả high confidence.
- Với mỗi distinct character được dùng trong top, so target với ít nhất một
  exemplar cùng case; với cặp dễ nhầm, dùng hai exemplar hoặc một exemplar có
  từ ba lần lặp.
- Với rule key mơ hồ, mở record và rule-layers của exemplar để so trực tiếp
  glyph font ở phía rules.
- Nếu filename có trong review_labels.json, dùng review record để verify và nói
  rõ không phải blind decode. Nếu chỉ có trong test_labels.json, không coi record
  đó là ground truth hay exemplar. Khi đánh giá blind, loại filename mục tiêu.

## 2. Các bất biến cần giữ

1. Đọc đủ 5 rules trước khi chốt top string. Rules tạo ra tập ứng viên mạnh,
   nhưng không bắt buộc cả 5 key đều xuất hiện ở top.
2. Mọi ký tự top đã chốt phải có key tương ứng trong rules. Nếu thiếu mapping,
   chưa được tạo final_output.
3. Matching với rules không phân biệt hoa/thường, nhưng raw_top_string phải
   giữ đúng case nhìn thấy nếu đang tạo hoặc review nhãn.
4. Không mặc định một màu bằng một ký tự. Một màu có thể chỉ chứa noise, chứa
   glyph bị chia mảnh, hoặc có đường noise cùng màu nối vào glyph.
5. Trước khi thật sự nhìn nhận hoặc đặt tên glyph, bắt buộc phát hiện và loại
   các line nhiễu liên tục dày đúng 1 px khỏi bản làm việc. Line 1 px có thể
   tạo ảo giác stem, crossbar, slash, bowl hoặc nối hai mảnh không liên quan.
6. Không dùng pixel trái nhất của toàn lớp màu để sắp thứ tự. Pixel đó thường
   thuộc đường noise dài.
7. Không coi opening 2x2 là ảnh thật. Nó chỉ là locator; chỉ sau khi đã nhận
   dạng trên residual mới quay lại mask gốc để kiểm tra nét mảnh, dấu chấm,
   đuôi và vòng nhỏ.
8. Chỉ kết luận glyph ở high confidence khi có ít nhất hai dấu hiệu độc lập,
   thí dụ cấu trúc vùng kín cộng hướng đuôi, hoặc số thân dọc cộng số thanh ngang.
9. Nếu không thấy đủ dữ liệu, ghi uncertain và nêu tối đa hai ứng viên trong
   rules. Khi cần ghi nhãn tự động, chọn ứng viên xếp hạng cao nhất làm dự đoán
   provisional; không gắn reviewed và không bịa ký tự ngoài rules.

## 3. Quy trình phân tích có thể kiểm chứng

### Bước 1: Xác nhận ảnh và vùng làm việc

- Chỉ phân tích khi công cụ thực sự đã trả về ảnh.
- Phóng ảnh bằng nearest-neighbor để giữ nguyên pixel; không dùng nội suy mượt.
- Tách vùng top khoảng y=0..49 và vùng rules ở nửa dưới. Điều chỉnh crop nếu
  nội dung thực tế lệch khỏi mẫu.
- Khi có scripts/prepare_layers.py, bắt buộc chạy helper này trước khi đọc:

      python scripts/prepare_layers.py <đường-dẫn-ảnh> --scale 6

- Helper tạo overview cùng các sheet raw, detected-bands, residual và locator
  trong thư mục tạm. Thứ tự nhìn bắt buộc là: residual đã lọc line 1 px trước,
  locator tiếp theo, raw layer sau cùng để kiểm chứng. Không nhìn composite/raw
  trước rồi mới cố sửa ấn tượng ban đầu.
- Top sheets của helper phải là một cột, mọi panel dùng chung trục x cục bộ
  0..127. Nếu output top có hai cột thì helper đã cũ: dừng, chạy lại helper hiện
  tại. Không bao giờ sắp glyph theo cột, thứ tự màu hoặc global x của sheet PNG.
- detected-bands/residual là ước lượng Hough để phát hiện line dài, không phải
  ground truth. Dùng residual để nhận dạng và dựng skeleton trước. Sau khi đã
  có candidate, dùng raw layer chỉ để phục hồi nét glyph cục bộ bị line che;
  không cho một line dài trên raw layer lật ngược topology của residual.

### Bước 2: Đọc bảng rules

- Đọc đúng 5 cặp key -> digit.
- Trong rule-layers, mỗi mapping cục bộ thường nằm trọn trong một mask màu;
  các màu không chứa rule text chỉ còn đường noise. Đọc text cục bộ và bỏ qua
  đường cùng màu kéo dài ra ngoài bbox text.
- Trước khi đọc key, arrow hoặc digit, loại các line liên tục dày đúng 1 px
  khỏi ROI làm việc. Chỉ bắt đầu nhận dạng rule text trên residual đã lọc.
- Với mỗi mapping, tìm cụm mũi tên -> và digit trước, rồi chia cục bộ thành ba
  ROI key | arrow | digit. Chỉ phân loại pixel trong ROI key; không lấy toàn bộ
  component cùng màu làm key.
- Một band thẳng/chéo đi vào rồi đi ra khỏi hai cạnh ROI, hoặc tiếp tục ra
  ngoài ROI quá một bề rộng glyph, là noise ngay cả khi dày 2-5 px và sống sót
  sau opening.
- Không chốt top cho tới khi cả 5 mapping đều có confidence high. Nếu một key
  hoặc digit vẫn medium, phóng riêng mask đó và so với glyph/digit đã có nhãn;
  nếu vẫn mơ hồ thì dừng ở trạng thái uncertain.
- Confidence của rule key chỉ lên high sau khi topology khớp ít nhất một rule
  glyph có nhãn trong references/labeled-exemplars.md. r có rule-only exemplar;
  m chưa có exemplar review.
- Với key có bowl, ước lượng baseline cục bộ từ arrow/digit và đáy bowl, rồi
  kiểm tra thêm các hàng raw-mask ngay dưới nó:
  - p có stem trái tiếp tục dưới bowl;
  - q có stem phải tiếp tục dưới bowl;
  - a kết thúc tại baseline;
  - D/P hoa không có descender.
- Rule font chỉ cao khoảng 11 px. Một fragment sạch rộng 2 px trong một hàng
  ngay dưới baseline vẫn là bằng chứng descender nếu nối trực tiếp đúng cạnh
  bowl; không áp ngưỡng của top glyph một cách máy móc.
- Đối chiếu key với rule alphabet ở Mục 1. Rule letters được lưu lowercase dù
  glyph hiển thị có thể trông như case khác.
- Nếu key trông giống một special ngoài #, $, %, &, @, coi đó là ảnh hưởng
  của line hoặc lỗi đọc và phân tích lại; không đưa ký tự đó vào candidate set.
- Chuẩn hóa chữ cái sang lowercase chỉ trong bản mapping dùng để tính output.
  Không lowercase raw_top_string.
- Nếu thấy i, o hoặc y ở rule, cho phép giữ key lowercase: review đã xác nhận
  chúng có thể là distractor không dùng ở top. Nếu thấy g hoặc j, đọc lại và báo
  xung đột vì review hiện không có hai key này.
- Nếu thấy ký hiệu giống 0/0 ở vị trí key, kiểm tra ba neo của dấu % trước khi
  ghi key là %.

### Bước 3: Tách lớp màu ở ảnh bgonly

Các ảnh bgonly đã kiểm tra dùng nền RGB 128,128,128 và bảy màu foreground:

- RGB 255,0,0
- RGB 0,255,0
- RGB 0,0,255
- RGB 255,255,0
- RGB 255,0,255
- RGB 255,255,255
- RGB 0,0,0

Trước khi dựa vào palette, kiểm tra ảnh hiện tại thực sự chỉ có các màu này.
Sau đó tạo một binary mask cho từng màu foreground.

Trên mỗi mask:

- Giữ một bản gốc.
- Phát hiện trước các run thẳng hoặc gần thẳng dày đúng 1 px, kéo dài qua nhiều
  ký tự hoặc đi vào rồi đi ra khỏi ROI. Xóa chúng khỏi bản working mask trước
  mọi nhận dạng thị giác.
- Fragment glyph ngắn có bề rộng 1 px không tự động là line. Chỉ xóa khi nó
  đồng thời mảnh, gần thẳng và có phạm vi dài bất thường so với bbox glyph.
- Từ working mask đã lọc 1 px, tạo locator bằng opening 2x2 hoặc tiêu chí tương
  đương để giảm noise còn lại.
- Tìm cụm cục bộ có chiều rộng và chiều cao của glyph. Đường noise thường dài
  qua nhiều ô, còn glyph tạo cụm ngắn, dày và có nhiều hàng lân cận.
- Lọc noise theo phạm vi và cách kết thúc, không chỉ theo độ dày. Một line dày
  vẫn là noise nếu gần thẳng, xuyên nhiều ô và không kết thúc hợp lý trong
  glyph. Có thể dùng run-length, Hough/RANSAC hoặc kiểm tra line extent trên
  bản locator.
- Trừ line 1 px và band dài trên working/locator, không phá hủy bản gốc. Tại
  giao điểm, chỉ quay lại mask gốc sau khi đã nhận dạng candidate để phục hồi
  phần stroke glyph hợp topology; không phục hồi toàn bộ line.
- Bỏ lớp chỉ còn các đường dài mảnh.
- Nếu opening làm đứt glyph thành nhiều đảo gần nhau, gom các đảo có cùng vùng
  x và cùng envelope chiều cao; không đếm mỗi đảo là một ký tự.
- Trước khi tạo slot cho special rời mảnh, gom component cùng màu thành một
  envelope khi có hai island dạng loop và một chuỗi diagonal cục bộ nằm giữa.
  Dấu % vốn có thể gồm ba component; một loop riêng không phải một slot.
- Quay lại mask gốc để khôi phục dấu chấm, vòng nhỏ, crossbar, tail và đoạn nét
  bị opening xóa.

Quy tắc tri giác quan trọng: residual không có line 1 px là ảnh nhìn nhận chính.
Mask gốc chỉ là bằng chứng phụ sau nhận dạng. Tại giao điểm, line có thể xóa
hoặc chia nét glyph; nối lại khoảng trống chỉ khi hai đầu stroke thẳng hàng và
việc nối đó phù hợp topology của candidate trong rules.

### Bước 4: Chuẩn hóa xoay trái/phải và lập bản đồ che khuất

Chỉ thực hiện bước này trên từng ROI glyph cục bộ sau khi đã tách đúng màu và
loại line 1 px. Không dùng composite thô để đoán hình chữ, vì chữ màu khác đè
lên và line nhiễu có thể tạo stem, bar hoặc diagonal giả.

Quy trình che khuất dưới đây áp dụng như nhau cho mọi chữ hoa, chữ thường và
năm special hợp lệ trong Mục 5-6. Không giả định chỉ phần đầu bị che: màu khác
có thể xóa apex, stem, bowl, counter, bar, junction, chân, đáy, tail hoặc gần
như toàn bộ một glyph. Ví dụ P ở Mục 5 chỉ minh họa cách dùng quy trình, không
phải ngoại lệ duy nhất được phép suy từ các mảnh còn lại.

Với mỗi slot:

1. Lấy ROI của cụm glyph cùng màu trên residual, thêm padding đủ để không cắt
   apex, bowl, tail hoặc descender. Giữ song song: residual mục tiêu, raw mask
   mục tiêu, mask line đã loại và mask của từng màu khác trong cùng ROI.
2. Ước lượng glyph chỉ nghiêng/xoay nhẹ sang trái hoặc phải. Nếu có stem dài
   đáng tin, dùng hướng stem hoặc trục chính/PCA làm gợi ý. PCA không phải đáp
   án khi glyph có nhiều diagonal, nhiều component hoặc bị che mạnh.
3. Khi hướng chưa rõ, thử bản sao ROI từ âm 40° đến dương 40° theo bước 10°.
   Sau đó tinh chỉnh quanh góc tốt nhất theo bước 5°. Xoay bằng nearest-neighbor,
   không nội suy mượt. Quy ước và ghi rõ dấu góc trong báo cáo; thí dụ góc
   dương là xoay theo chiều kim đồng hồ để đưa glyph về thẳng.
4. Chọn góc làm cho các stem dự kiến gần dọc, bar gần ngang, baseline/cap-height
   hòa với glyph lân cận và topology khớp tốt nhất. Không chọn góc chỉ vì pixel
   overlap tăng. Không mirror, không lật trên-dưới và không thử 90°/180°; corpus
   này chỉ cho phép nghiêng trái/phải.
5. Chỉ sau khi có bản canonical mới áp dụng các từ trên/dưới, trái/phải, bowl
   trên, leg dưới-phải, ascender, baseline và descender trong Mục 5-7. Góc là
   phép chuẩn hóa để đọc cấu trúc, không phải phép tạo thêm stroke.
6. Dựng occlusion map bằng union các mask màu khác đi qua envelope mục tiêu,
   cộng mask line đã loại. Một neo bị thiếu chỉ được coi là "có thể bị che" khi
   đường đi dự kiến của stroke chạm trực tiếp vùng che này, hai đầu fragment
   còn lại gần thẳng hàng/tiếp tuyến hợp lý và phần nối phù hợp candidate.
7. Vùng trống không có màu khác hoặc line đi qua là bằng chứng chống lại
   candidate. Phân biệt rõ ba loại: stroke quan sát được, stroke có thể bị che,
   và stroke bị thiếu không giải thích được. Không vẽ/nối stroke suy đoán vào
   residual và không biến phần "có thể bị che" thành bằng chứng đã quan sát.
8. Chấm topology trước silhouette: số component hợp lý sau khi gom mảnh, số
   loop/counter, endpoint, junction, crossing, cặp stroke song song, hướng mở
   và quan hệ stem-bowl. Chấm độ khớp contour/pixel sau cùng, vì xoay và chồng
   màu làm contour dễ khuyết.
9. Nếu một candidate chỉ thắng khi phải giả nhiều stroke ở vùng không bị che,
   loại candidate đó. Nếu hai candidate chỉ khác ở một neo đúng vùng bị che,
   giữ cả hai và hạ confidence để người dùng duyệt.

Với từng candidate còn hợp lệ trong rules, lập một anchor ledger riêng:

- Liệt kê mọi neo bắt buộc từ từ điển, không chỉ neo đang dễ thấy. Gắn từng neo
  vào vị trí canonical dự kiến rồi đánh dấu observed, plausibly occluded hoặc
  unexplained missing. Một neo bị che hoàn toàn không được tính như bằng chứng
  dương; nó chỉ làm giảm mức phạt cho việc neo không xuất hiện.
- Gán mỗi fragment quan sát được cho một stroke/neo cụ thể. Hai mảnh cùng màu
  chỉ được xem là hai đầu của cùng stroke khi sau dựng đứng chúng có hướng,
  độ dày, tiếp tuyến và khoảng cách hợp lý, đồng thời khoảng giữa nằm trên
  occlusion map. Cùng màu nhưng khác slot hoặc khác đường đi không được nối.
- Chấm mạnh neo phân biệt quan sát được; thí dụ bowl dưới thật chống lại P,
  crossing thật chống lại Q, outer loop thật chống lại a, stem thật chống lại
  C và hai loop góc thật chống lại X. Một stroke mâu thuẫn đang nhìn thấy không
  thể bị giải thích là "do che khuất".
- Nếu chỉ còn một neo chung chung như một stem, một cung hay một slash, identity
  phải ở low confidence và giữ tối đa hai candidate trong rules. Medium cần ít
  nhất hai neo độc lập hoặc một neo phân biệt mạnh cộng các neo còn thiếu đều
  nằm đúng vùng che. High cần có neo phân biệt quan sát được; không cấp high chỉ
  nhờ suy hoàn thiện qua vùng bị che.

Kiểm tra theo họ cấu trúc để bao phủ mọi dạng mất nét:

- Stem/bar: E, F, H, L, T, f, t, l và #. Kiểm tra số stem/bar, cao độ và điểm
  nối; line dài đã loại không được phục hồi thành bar. Nếu một bar mất, đường
  bar dự kiến phải kết thúc tại vùng che chứ không ở vùng trống sạch.
- Bowl/loop/counter: a, b, d, e, p, q, B, D, P, Q, R, @, & và %. Theo dõi số
  vòng, vị trí vòng, phía của stem, outer/inner nesting và tail/leg. Khi counter
  bị chia đôi bởi chữ khác, dùng hai cung cùng tiếp tuyến; không tự khép loop.
- Cung hở/lobe: C, c, S, s, U, u, $, &. Giữ hướng mở và thứ tự đổi độ cong;
  vùng che có thể giấu khe mở nhưng không được biến một cung quan sát thành
  stem hoặc diagonal.
- Shoulder/hump: h, n, m, r. Kiểm tra ascender, số hump, độ khép của shoulder
  và terminal phải; một hump bị chia bởi màu khác chỉ được nối khi hai fragment
  giữ cùng tiếp tuyến. Không suy n thành m nếu vị trí hump thứ hai không bị che.
- Diagonal/junction: A, K, k, M, N, V, v, W, w, X, x, Z, z, # và %. Theo dõi
  thứ tự endpoint, apex/valley, junction/crossing và diagonal có tiếp tục qua
  nút hay không; không suy topology chỉ từ silhouette nghiêng.
- Ascender/descender/case: mọi cặp hoa-thường, đặc biệt b/d/p/q, f/t/l/h/k và
  các cặp chỉ khác nhiều ở kích thước. Nếu đỉnh hoặc đáy bị che, dùng các glyph
  lân cận để ước lượng cap-height, x-height, baseline và descender line rồi hạ
  confidence case; không đổi case để vừa phần bbox còn sót.

Một glyph có thể thuộc nhiều họ; phải áp dụng tất cả kiểm tra liên quan. Mục
tiêu là giải thích các pixel quan sát được và vị trí mất nét, không phải dựng
một hình hoàn chỉnh đẹp mắt.

Các kiểm tra bắt buộc sau chuẩn hóa góc:

Danh sách sau là các confusion gate bổ sung, không giới hạn quy trình che khuất
chỉ vào những nhóm này.

- b/d/p/q: xác định bowl ở bên nào và stem đi lên hay xuống baseline; xoay
  không bao giờ được dùng như phép lật để đổi b thành d hoặc p thành q.
- P/B/R/F/D: tìm stem trái trước, rồi phân biệt bowl trên, bowl dưới, leg chéo,
  bar thẳng và cung toàn chiều cao. Một line chéo đã bị loại không phải leg R.
- M/W: đếm và định vị peak/valley sau khi dựng đứng; không phân loại theo bbox
  nghiêng ban đầu.
- N/Z: N có hai stem nối bằng diagonal; Z có hai bar ngang nối bằng diagonal.
- U/V: đáy U tròn, đáy V là một junction nhọn ngay cả khi hai cạnh đang nghiêng.
- Q/@/&: Q có một vòng chính và tail, @ có vòng ngoài bao nhân, & có hai lobe
  bất đối xứng cùng crossing/tail.
- %/X: % có loop/chấm ở hai góc và một slash; X có hai diagonal đi xuyên qua
  một crossing. Không cho slash của % biến thành nhánh X sau khi deskew.

### Bước 5: Xác định case và khung chữ

Ước lượng bốn đường tham chiếu từ nhiều glyph cùng ảnh:

- cap-height: đỉnh chung của chữ hoa;
- x-height: đỉnh của chữ thường thấp như a, c, e, n, s, u, v, w, x, z;
- baseline: đáy chung của glyph không có descender;
- descender line: đáy của p hoặc q.

Các chữ thường b, d, f, h, k, l, t có ascender. Chữ p và q có descender.
Khi cap-height hoặc baseline bị che, case của C/c, S/s, U/u, V/v, W/w, X/x,
Z/z có thể chỉ khác ở kích thước; hạ confidence thay vì đoán chắc.

Đo case trên envelope canonical sau chuẩn hóa góc, không đo chiều cao bbox đang
nghiêng. So cap-height/x-height/baseline với nhiều glyph lân cận; một glyph bị
che đỉnh hoặc đáy không được tự động đổi case chỉ vì bbox quan sát bị thấp.

### Bước 6: Sắp thứ tự top

- Trước khi ghép chuỗi, lập inventory có đúng 5 hoặc 6 dòng dạng
  `[color, panel-local anchor_x, candidate, case, confidence]`.
- Tọa độ của mọi màu phải đo trên cùng trục x cục bộ 0..127 của ảnh gốc. Không
  so global x giữa các panel của sheet, không đọc hết cột trái rồi cột phải và
  không dùng thứ tự palette.
- Với mỗi glyph-bearing component, lấy canonical placement anchor của cụm dày
  sau khi loại noise. Glyph xoay có thể làm bbox vượt qua hàng xóm; khi đó dùng
  stem, symmetry axis hoặc baseline intercept sau dựng đứng, không dùng raw
  x-min hay centroid của line noise.
- Sắp inventory theo panel-local anchor_x rồi mới ghép raw_top_string.
- Direction đã xác nhận cho corpus hiện tại là x tăng dần, trái sang phải.
  Khi kiểm tra direction, khóa identity và màu từ lần đọc mù rồi mới so anchor;
  không tái gán màu theo đáp án đã biết để suy ra direction.
- Một component có anchor gần trùng slot đã có và phần lớn pixel nối vào các
  band dài không tạo một slot độc lập.
- Khi hai glyph chồng lấn mạnh, dùng vị trí slot, phần thân chính và baseline
  để kiểm tra chéo.
- Kết quả phải có đúng 5 hoặc 6 vị trí. Nếu có 4, tìm glyph bị che; nếu có 7,
  kiểm tra lớp noise hoặc glyph bị tách đôi.
- Case audit `auto_1783136756_569d44e3b5.png` là hard regression: thứ tự đúng
  `UU#UUU`, không phải `UUU#UU`. Sai thành dạng sau nghĩa là đã đọc sheet column.

### Bước 7: Nhận dạng bằng tập ứng viên

Cho từng vị trí:

1. Trước khi đặt tên, ghi skeleton trung lập trên bản canonical: số stem,
   bowl/counter, bar, endpoint, junction, diagonal, crossing, cặp song song,
   hướng mở, ascender, descender và tail. Không cho tên ứng viên ảnh hưởng bước
   thống kê này.
2. Chỉ so sánh với các key thực sự có trong rules, sau khi áp dụng giới hạn
   alphabet.
3. So hình thái với từ điển ở Mục 5 và Mục 6.
4. Ưu tiên topology: số thân, số vòng kín, số giao điểm, hướng mở, ascender,
   descender và tail.
5. Màu chỉ dùng để tách layer, không mang ý nghĩa ký tự hay case.
6. Với từng neo bắt buộc, ghi observed, plausibly occluded hoặc unexplained
   missing theo occlusion map. Nếu ứng viên tốt nhất vẫn có neo thiếu không
   giải thích được, đọc lại rules và mask; không ép hình lạ thành ứng viên gần
   nhất.
7. Trước khi giải từng slot độc lập, cluster các ROI canonical gần đồng dạng
   trong chính ảnh. Ba hoặc bốn glyph cùng topology mặc định cùng identity cho
   tới khi có neo loại trừ rõ; màu và rotation không phải neo loại trừ. Case
   regression `auto_1783136130_b8c45bc03c.png` phải ra `VVVVQ`.
8. Chạy duplicate-before-leftover check như một hard veto: căn bbox/envelope
   theo cap-height/baseline và so slot mơ hồ với các glyph rõ trong chính ảnh
   sau phép dịch ngang. Nếu stem/bowl/skeleton khớp glyph đã có, không được xét
   key chưa dùng cho tới khi chứng minh được một neo loại trừ.
9. Nếu hai slot có skeleton gần như đồng dạng nhưng đang bị gán hai tên khác,
   bắt buộc so lại mask gốc và band noise.
10. Không dùng việc một key chưa xuất hiện làm bằng chứng. Top được phép lặp,
   còn rule key được phép không xuất hiện.
11. Trước khi nâng identity lên high, so với exemplar cùng case theo chỉ mục
    có nhãn. Nếu target không giữ được các neo bền vững của exemplar, hạ về
    candidates/uncertain.

Khi chạy chế độ ghi nhãn tự động, mọi slot vẫn phải có một best candidate để
tạo record. Nếu không đạt high, dùng candidate đứng đầu sau toàn bộ veto làm
dự đoán provisional, giữ candidate thứ hai trong báo cáo review và tuyệt đối
không thêm reviewed vào record.

Không deskew/xoay ảnh gốc. Mọi góc thử chỉ áp dụng cho bản sao ROI cục bộ theo
Bước 4 và không được dùng để tạo, làm dày hoặc nối thêm stroke.

Hai bẫy đã lộ trong kiểm thử mù:

- Chỉ nhận % khi slash chéo nằm cục bộ trong bbox cùng ít nhất một loop và dấu
  vết loop đối góc. Hai đảo do opening tạo ra nhưng không có slash không phải %.
- Với %, duplicate check dùng union raw-mask của toàn envelope loop | slash |
  loop sau khi bỏ band dài, không dùng largest component hay một ROI con.
- Chỉ nhận h khi có left ascender liên tục ở vùng trên x-height, tiếp tục xuống
  thân, cộng shoulder/leg phải. Một đường ngang dài nối vào cụm không phải hump.

### Bước 8: Tính output và kiểm tra

- Tạo normalized_rules bằng cách lowercase key chữ cái; giữ nguyên đúng năm
  special #, $, %, &, @.
- Trước khi mapping, xác nhận mọi non-letter trong rules và raw_top_string đều
  thuộc đúng tập #, $, %, &, @; nếu có ký tự khác thì dừng và đọc lại ảnh.
- Với từng raw char, lookup bằng char.lower().
- final_output phải có cùng độ dài với raw_top_string.
- Tính lại final_output lần thứ hai từ dữ liệu đã chốt trước khi trả kết quả.
- Trước khi ghi, mọi raw character sau normalize phải là một trong đúng 5 rule
  keys và final_output phải chỉ chứa digit. Nếu không, record chưa được giải;
  không được ghi provisional bằng ký tự ngoài rules.

## 4. Quy ước hình thái chung của font

- Font sans-serif, nét pixel cứng, gần như không anti-alias.
- Chữ hoa thường cao gần cap-height và rộng hơn chữ thường.
- Chữ thường thấp nằm trong x-height, trừ nhóm ascender và descender.
- Nét bị màu khác ghi đè tạo khoảng trống. Khoảng trống do occlusion không
  đồng nghĩa glyph vốn hở.
- Đường noise dài có thể giả một crossbar hoặc foot. Chỉ tính một thanh nếu nó
  nằm cục bộ trong bbox glyph và nối hợp lý với cấu trúc cùng màu.
- Khi một ký tự chỉ còn mảnh, tìm neo bền vững trước: apex, stem side, loop,
  counter, crossing, tail hoặc descender.

## 5. Từ điển chữ cái: hoa, thường và điểm phân biệt

### A / a — đã có nhãn

- A hoa: hai nét chéo hội tụ thành apex ở trên, một crossbar giữa và thường có
  counter hình tam giác. Hai chân tách ra ở baseline.
- a thường: một bowl nhỏ ở x-height kèm stem hoặc terminal bên phải; không có
  apex nhọn và không có hai chân chéo dài.
- Phân biệt: A khác H và # ở apex cùng hai cạnh chéo; a khác @ vì không có
  outer loop bao trọn, khác q vì không có descender phải và khác d vì không có
  ascender cao bên phải.
- Khi bị che: apex cộng một phần crossbar là cặp neo mạnh nhất của A; bowl cộng
  terminal phải là cặp neo mạnh của a.

### B / b — đã có nhãn

- B hoa: một stem dọc liên tục bên trái và hai bowl xếp trên-dưới mở sang phải.
  Có thể còn hai counter hoặc hai cung phải ngay cả khi giữa bị che.
- b thường: ascender dọc bên trái cao tới cap-height và chỉ một bowl thấp ở
  bên phải trong vùng x-height.
- Phân biệt: B khác P ở bowl dưới; khác D ở hai bowl thay vì một cung lớn; khác
  & vì B có spine trái thẳng. b khác d ở phía ascender.
- Khi bị che: xác định stem trái trước, rồi đếm dấu vết bowl ở nửa trên/dưới.

### C / c — đã có nhãn

- C hoa: một cung lớn cao gần cap-height, mở sang phải, không có stem đóng bên
  phải và không có ba thanh ngang.
- c thường: cùng topology nhưng thấp trong x-height và thường hẹp hơn.
- Phân biệt: C/c khác E/e vì không có stem trái cộng hệ thanh; khác Q vì không
  khép vòng và không có tail. O/o bị cấm nên một oval tưởng là O phải được đọc
  lại theo các ứng viên Q, @, a hoặc c trong rules.
- Khi bị che: tìm khe mở bên phải và độ liên tục của cung trái.

### D / d — D có top review; d hiện chỉ có rule evidence

- D hoa: stem dọc bên trái cộng một cung lớn nối từ đỉnh đến đáy, tạo một
  counter lớn toàn chiều cao.
- d thường: bowl nhỏ ở x-height nằm bên trái một ascender dọc bên phải.
- Phân biệt: D khác B ở một counter lớn; khác P ở cung kéo tới baseline. d khác
  b ở phía ascender, khác q vì ascender đi lên chứ không descender đi xuống.
- Khi bị che: dùng phía của stem và việc stem vượt lên trên hay xuống dưới bowl.
- Không nâng d thường ở top lên high chỉ từ test-only exemplar; so rule glyph d
  đã review và giữ confidence phù hợp cho tới khi có top review.

### E / e — đã có nhãn

- E hoa: stem trái cộng ba thanh sang phải ở đỉnh, giữa và đáy; thanh giữa
  thường ngắn hơn.
- e thường: vòng cong nhỏ ở x-height với một nét ngang trong/giữa và khe mở
  sang phải.
- Phân biệt: E khác F nhờ thanh đáy; khác C nhờ stem và thanh giữa. e khác c
  nhờ nét ngang giữa và dấu vết loop.
- Khi bị che: không đếm đường noise dài là thanh; phải thấy thanh cục bộ nối
  với stem hoặc loop.

### F / f — đã có nhãn

- F hoa: stem trái cao, thanh trên rộng và thanh giữa ngắn hơn; không có thanh
  đáy.
- f thường: glyph hẹp có ascender, đầu trên cong hoặc hook sang phải và một
  crossbar ngắn quanh x-height; phần thân có thể xuống sát baseline.
- Phân biệt: F khác E ở việc thiếu đáy; khác T vì có stem lệch trái và thêm
  thanh giữa. f khác t ở đầu cong và tỷ lệ crossbar; khác l ở crossbar.
- Khi bị che: với F cần stem cộng hai thanh ở hai cao độ; với f cần ascender
  cong cộng crossbar ngắn.

### H / h — đã có nhãn

- H hoa: hai stem dọc cao bằng nhau nối bởi đúng một crossbar ở giữa.
- h thường: ascender dọc bên trái và một hump cong sang phải trong x-height;
  không có hai stem cao bằng nhau.
- Phân biệt: H khác # ở một crossbar thay vì hai; h khác n ở ascender cao; khác
  b vì hump không tạo bowl kín rõ.
- Khi bị che: đo chiều cao hai phía và xác định crossbar thẳng hay hump cong.

### K / k — đã có nhãn

- K hoa: stem dọc bên trái; hai diagonal gặp stem tại nút giữa rồi tỏa lên-phải
  và xuống-phải.
- k thường: vẫn có ascender trái cao, nhưng hai arm/leg nhỏ hơn và gặp nhau
  quanh x-height.
- Phân biệt: K/k khác X/x vì có stem trái liên tục; khác R vì không có bowl.
- Khi bị che: nút ba nhánh tại stem trái là neo quan trọng hơn hình dáng từng
  diagonal.

### L / l — đã có nhãn

- L hoa: stem dọc cao bên trái cộng foot ngang dài ở baseline.
- l thường: chủ yếu là một ascender dọc đơn, đôi khi có hook hoặc foot rất
  ngắn; không có foot dài cục bộ như L.
- Phân biệt: l khác t/f ở việc thiếu crossbar; I/i bị cấm. Chữ số 1 chỉ ở rule
  value và thường có hook đỉnh nhưng không phải top glyph.
- Khi bị che: chỉ công nhận foot của L nếu nó nối cục bộ cùng màu với stem;
  đường noise chạy xuyên nhiều ô không phải foot.

### M / m — người dùng xác nhận hợp lệ, chưa có top exemplar review

- M hoa: hai stem ngoài cao; hai diagonal đi từ đỉnh ngoài xuống một valley ở
  giữa rồi đi lên, tạo cấu trúc đối xứng với hai đỉnh ngoài.
- m thường: stem trái ở x-height nối hai hump liên tiếp sang phải; không có
  descender.
- Phân biệt: M khác W ở hướng cấu trúc: M có valley giữa đi xuống từ hai đỉnh,
  W có hai valley ở đáy và một đỉnh giữa. m khác n vì có hai hump thay vì một.
- Vì chưa có ảnh nhãn hiện tại, coi các mô tả này là chuẩn typography do người
  dùng xác nhận về alphabet, không tuyên bố đã hiệu chỉnh đúng font. Khi gặp
  M/m thật, lưu filename đã xác nhận để bổ sung bằng chứng sau.

### N / n — đã có nhãn

- N hoa: hai stem dọc cao nối bởi một diagonal từ góc trên-trái đến dưới-phải
  hoặc chiều tương đương theo orientation của font.
- n thường: stem trái thấp cộng một hump sang phải, toàn bộ nằm trong x-height.
- Phân biệt: N khác H vì diagonal thay cho crossbar; n khác h vì không có
  ascender cao, khác m vì chỉ một hump, khác # vì không có lưới giao điểm.
- Khi bị che: tìm hai đầu diagonal nối hai stem của N; với n tìm shoulder cong.

### P / p — đã có nhãn

- P hoa: stem trái cao, một bowl ở nửa trên và phần dưới bên phải để mở.
- p thường: bowl nhỏ trong x-height với stem trái kéo xuống dưới baseline thành
  descender.
- Phân biệt: P khác B nhờ thiếu bowl dưới; khác R nhờ thiếu leg chéo; p khác q
  ở phía descender và khác b vì thân đi xuống thay vì lên.
- Ví dụ che khuất theo thuật toán chung: P vẫn có thể được xét khi quan sát
  được stem trái cùng các mảnh
  cong hoặc điểm nối của đúng một bowl trên, còn đường dự kiến của phần bowl
  mất đi chạy qua mask chữ màu khác. So lần lượt: B cần bằng chứng bowl dưới;
  R cần leg chéo xuống-phải; F có upper/middle bar thẳng thay vì cung bowl; D có
  một cung/counter lớn kéo gần toàn chiều cao. Không dùng việc "không nhìn thấy"
  các neo này làm bằng chứng nếu vùng tương ứng đang bị che.
- Nếu chỉ còn một stem, hoặc stem cộng một fragment không đủ xác định là cung
  hay bar, không ép thành P. Trả tối đa hai candidate phù hợp rules, liệt kê
  phần bowl/leg/bar nào có thể bị che và phần nào thiếu không giải thích được.
- Case chỉ chốt sau khi dựng đứng ROI: P hoa dừng ở baseline; p thường có stem
  trái đi xuống descender line. Một đỉnh bị che không tự biến P thành p.

### Q / q — đã có nhãn

- Q hoa: một oval/counter lớn ở cap-height cộng tail ngắn thoát ở góc
  dưới-phải, thường chéo qua hoặc nhô khỏi vòng.
- q thường: bowl nhỏ ở x-height cộng descender dài ở phía phải.
- Phân biệt: Q/q khác @ vì chỉ có một vòng chính, không có outer loop bao nhân;
  khác & vì không có hai lobe tự giao; q khác p ở phía descender.
- O/o bị cấm: nếu tail của Q bị che, chỉ chọn Q khi Q có trong rules và có dấu
  vết tail hoặc cấu trúc còn lại phù hợp; nếu không, ghi uncertain.

### R / r — hợp lệ nhưng hiếm; r mới có rule-only exemplar review

- R hoa: giống P ở stem trái và bowl trên, cộng một leg chéo đi từ nút giữa
  xuống-phải tới baseline.
- r thường: stem ngắn ở x-height cộng shoulder hoặc arm ngắn sang phải; thường
  không tạo bowl kín.
- Phân biệt: R khác P bằng leg chéo; khác K vì R có bowl trên; r khác n vì
  shoulder không khép thành hump đầy đủ.
- Vì chưa có ảnh nhãn hiện tại, không dùng chi tiết vi mô như độ cong hay độ
  dài leg làm luật cứng. Cần ít nhất bowl/shoulder cộng leg/arm đúng hướng.

### S / s — đã có nhãn

- S hoa: hai cung ngược chiều tạo lobe trên và dưới, cao gần cap-height, không
  có stem thẳng liên tục.
- s thường: cùng topology nhưng thấp trong x-height và thường hẹp hơn.
- Phân biệt: S/s khác $ nhờ không có stem dọc xuyên tâm; khác Z/z ở đường cong
  thay vì hai thanh ngang nối bằng diagonal.
- Khi bị che: tìm sự đổi hướng của hai cung; không nhận $ nếu đoạn dọc chỉ là
  một đường noise dài khác màu hoặc không thuộc cụm glyph.

### T / t — đã có nhãn

- T hoa: một thanh ngang rộng ở cap-height và một stem dọc giữa đi xuống
  baseline; không có thanh giữa thứ hai.
- t thường: ascender hẹp, crossbar ngắn nằm thấp hơn đỉnh và có thể có hook
  hoặc foot nhỏ.
- Phân biệt: T khác F ở việc thiếu thanh giữa; t khác f ở đầu ít cong hơn và
  crossbar/terminal; khác l ở crossbar.
- Khi bị che: xác định cao độ của crossbar so với đỉnh glyph và độ cân giữa
  hai phía của thanh.

### U / u — đã có nhãn

- U hoa: hai stem dài từ cap-height nối bằng đáy tròn, mở hoàn toàn ở trên.
- u thường: hai nét thấp trong x-height nối đáy tròn, thường có terminal hoặc
  stem ngắn bên phải.
- Phân biệt: U/u khác V/v ở đáy tròn thay vì điểm nhọn; khác n vì n có hump
  đóng phía trên chứ không lòng chén mở trên.
- Khi bị che: đáy cong và hai cạnh đi lên là hai neo chính; dùng chiều cao để
  chốt case.

### V / v — đã có nhãn

- V hoa: hai diagonal dài hội tụ thành một điểm nhọn ở baseline.
- v thường: cùng topology nhưng thấp trong x-height.
- Phân biệt: V/v khác U/u ở đáy nhọn; khác W/w ở chỉ một valley; khác X/x vì
  hai nét không tiếp tục qua điểm giao lên phía đối diện.
- Khi bị che: tìm một apex đáy duy nhất và hướng hai nhánh đi lên.

### W / w — đã có nhãn

- W hoa: bốn diagonal tạo hai valley ở đáy và một đỉnh ở giữa, cao gần
  cap-height.
- w thường: cùng cấu trúc như hai chữ v nối nhau nhưng thấp trong x-height.
- Phân biệt: W/w khác V/v ở hai valley; khác M/m ở hướng đỉnh và valley; khác
  X/x vì không có một crossing trung tâm.
- Khi bị che: đếm số lần đổi hướng ở baseline; không suy W thành A chỉ vì mất
  một chân.

### X / x — đã có nhãn

- X hoa: hai diagonal dài cắt nhau tại vùng giữa, cao gần cap-height.
- x thường: cùng topology trong x-height.
- Phân biệt: X/x khác K/k vì không có stem trái; khác % vì không có hai
  loop/chấm đối góc; khác # vì chỉ hai diagonal và một crossing chính.
- Khi bị che: cần dấu vết của hai nét tiếp tục qua crossing; một slash đơn
  không đủ.

### Z / z — đã có nhãn

- Z hoa: thanh trên và thanh đáy nối bởi một diagonal, cao gần cap-height.
- z thường: cùng topology nhưng thấp trong x-height.
- Phân biệt: Z/z khác E/F vì không có stem dọc trái; khác S/s vì diagonal
  thẳng thay cho hai cung.
- Khi bị che: chỉ tính hai thanh nếu chúng thuộc bbox glyph, không phải hai
  đường noise ngang độc lập.

## 6. Ký tự đặc biệt và dấu hiệu bắt buộc

### Dấu #

- Cấu trúc chuẩn: hai stroke gần song song theo chiều dọc/chéo, bị cắt bởi hai
  stroke gần ngang, tạo bốn vùng giao cục bộ.
- Khác H: H chỉ có một crossbar.
- Khác E/F: E/F chỉ có một stem chính.
- Khác noise: bốn stroke phải nằm trong cùng bbox glyph, có độ dày cục bộ và
  các đầu nét nhô hợp lý; hai đường dài tình cờ cắt nhau không đủ.
- Khi bị che: cần thấy dấu vết của hai stem và ít nhất hai cao độ crossbar,
  hoặc nhiều giao điểm phù hợp với lưới.

### Dấu $

- Cấu trúc chuẩn: thân cong dạng S cộng một stem dọc xuyên vùng giữa. Stem có
  thể liền hoặc chỉ còn đoạn nhô ở trên/dưới do occlusion.
- Khác S/s: S/s không có stem dọc cùng màu thuộc cụm glyph.
- Khác &: $ không có hai loop bất đối xứng tự giao.
- Khác f/t: $ có hai lobe cong đổi hướng, không chỉ stem cộng crossbar.
- Khi bị che: nếu mất stem, chỉ hạ còn hai ứng viên S/$; tìm fragment dọc ở
  đầu trên, tâm hoặc đáy trước khi chốt.

### Dấu %

- Cấu trúc chuẩn có ba phần: loop/chấm trên-trái, slash chéo dài và
  loop/chấm dưới-phải. Các phần có thể rời nhau.
- Khác X/x: X/x có hai nét dài giao tại tâm, không có hai đảo tròn.
- Khác &: & là một đường cong liên kết/tự giao, không phải ba phần rời.
- Khác slash đơn: phải còn ít nhất một loop và dấu vết loop đối góc, hoặc hai
  loop đúng vị trí.
- Khi bị che: xác nhận ít nhất hai trong ba neo và kiểm tra vị trí neo thứ ba
  có bị layer khác ghi đè hay không.
- Không tách một loop khỏi envelope rộng rồi gọi nó là a/q. Nếu cùng màu còn
  loop thứ hai và slash ở vùng giữa, bắt buộc kiểm tra % trước.

### Dấu &

- Cấu trúc chuẩn: lobe trên và lobe dưới bất đối xứng, eo/crossing ở giữa và
  một terminal hoặc tail lệch phải.
- Khác B/b: & không có spine trái thẳng liên tục và hai bowl mở cùng một phía.
- Khác Q/q: Q/q có một vòng chính cộng tail, không có hai lobe tự giao.
- Khác @: @ có outer loop bao một nhân, còn & có hai phần xếp dọc/crossing.
- Khác $: & có vùng kín/nút cong; $ theo thân S cộng stem dọc.
- Khi bị che: dùng cặp neo crossing và tail hoặc hai lobe bất đối xứng; một
  đường cong lộn xộn đơn lẻ không đủ.

### Dấu @

- Cấu trúc chuẩn: một outer loop bao quanh bowl/nhân giống a, kèm đoạn thoát
  hoặc khe ở phía phải.
- Khác Q: Q chỉ có một counter lớn và tail dưới-phải; @ có cấu trúc lồng nhau.
- Khác a: a chỉ có bowl và stem/terminal, không có outer loop bao trọn.
- Khác &: & có hai lobe theo chiều dọc và crossing; @ là vòng ngoài bao nhân.
- Khi bị che: phải còn đồng thời dấu vết outer loop và ít nhất một phần inner
  bowl. Một vòng đơn hoặc một tail đơn không đủ để nhận @.

## 7. Ma trận phân biệt nhanh các nhóm dễ nhầm

### b / d / p / q

Dùng quy tắc phía-thân và hướng-thân:

- b: thân trái đi lên, bowl phải.
- d: bowl trái, thân phải đi lên.
- p: thân trái đi xuống, bowl phải.
- q: bowl trái, thân phải đi xuống.

Nếu ascender/descender bị che, chưa chốt cho tới khi kiểm tra baseline và rule
candidates.

### B / P / R / F / D / &

- B: stem trái cộng hai bowl.
- P: stem trái cộng một bowl trên.
- R: P cộng leg chéo xuống-phải; hợp lệ nhưng hiếm.
- F: stem trái cộng upper bar và middle bar thẳng, không có bowl cong.
- D: stem trái cộng một cung lớn toàn chiều cao.
- &: không có stem trái thẳng, có crossing/tail.
- Veto: không nhận & nếu không có crossing cùng hai lobe/tail; không nhận B
  nếu không có stem và dấu vết của hai bowl.
- Ví dụ P: nếu phần đầu bị chữ khác đè khuyết, chỉ suy bowl trên tại đúng vùng có
  occlusion; vùng trống sạch không được viện làm lý do. Nếu chỉ còn stem, giữ
  candidates thay vì buộc P/B/R/F/D.

### C / c / E / e / Q / q / @

- C/c: cung hở phải.
- E: stem cộng ba thanh; e: loop nhỏ cộng nét giữa.
- Q/q: một vòng/bowl cộng tail.
- @: outer loop bao inner bowl.
- O/o bị cấm; một hình oval thiếu tail phải được kiểm tra lại thay vì xuất O.

### H / h / n / #

- H: hai stem cao cộng một crossbar.
- h: ascender trái cộng một hump.
- n: stem thấp cộng một hump.
- #: hai stem cộng hai crossbar/nhiều giao điểm.

### E / F / L / T / f / t / l

- E có ba thanh, F hai thanh trên/giữa, L chỉ foot đáy, T chỉ thanh đỉnh.
- f và t có crossbar ngắn trong thân; f có đầu cong rõ hơn.
- l gần như stem đơn.
- Luôn bỏ ảnh hưởng đường noise dài trước khi đếm thanh.

### K / k / X / x

- K/k có stem trái liên tục và nút hai diagonal ở giữa.
- X/x không có stem, hai diagonal xuyên qua crossing.
- Veto: một loop/bowl không thuộc K; hai diagonal không tiếp tục qua crossing
  thì chưa đủ để nhận X.

### S / s / $ / Z / z

- S/s là hai cung, không stem.
- $ là S cộng stem dọc.
- Z/z là hai thanh nối bằng diagonal.

### U / u / V / v / W / w / M / m

- U/u có đáy tròn.
- V/v có một valley nhọn.
- W/w có hai valley đáy.
- M có valley giữa theo hướng ngược W; m có hai hump.
- Case của U/V/W và dạng thường chủ yếu dựa vào envelope chiều cao.

### A / a / H / #

- A có apex và hai chân chéo.
- a có bowl thấp cộng terminal.
- H có hai stem thẳng cộng một crossbar.
- # có lưới hai-by-hai.

### % / X / x / #

- % có hai đảo tròn đối góc cộng slash.
- X/x có hai diagonal, một crossing.
- # có bốn stroke và nhiều giao điểm.

### l / q, f / $, U / $, p / q

- l/q: nếu bowl biến mất sau khi bỏ line xuyên ROI, đọc lại là l; q cần bowl
  cục bộ cộng descender phải.
- f/$: $ cần hai lobe đổi hướng và stem dọc; f cần ascender với hook/crossbar
  cục bộ. Một stem cộng một mảnh cong không đủ để nhận $.
- U/$: hai side-stem và đáy tròn của U phải đều cục bộ; đáy do band chéo dài
  nối hai phía không được tính.
- p/q: chỉ chốt phía descender khi có ít nhất hai đoạn liên tiếp thuộc glyph
  và không nằm trên band dài; nếu không, giữ p/q uncertain.

## 8. Đọc chữ số ở rules

Không ép rule values chỉ vào số lẻ. Trong dữ liệu hiện tại:

- 1, 3, 5, 7, 9 là phổ biến.
- 4 đã được xác nhận trong các record đã review.
- 6 và 8 xuất hiện hiếm trong test labels; khi gặp phải đọc theo ảnh và báo
  confidence, không tự đổi.

Điểm phân biệt:

- 1: stem dọc, có thể có hook nhỏ ở đỉnh; không có cấu trúc giao kín của 4.
- 3: hai cung mở về trái, không có stem trái liên tục.
- 4: diagonal/vertical cộng crossbar, thường tạo vùng tam giác hoặc ngã ba.
- 5: thanh trên, đoạn trái phía trên và bowl dưới.
- 6: loop dưới cộng hook/cung đi lên.
- 7: thanh trên cộng diagonal đi xuống.
- 8: hai loop xếp dọc.
- 9: loop trên cộng stem/tail đi xuống.

Đường chéo noise qua digit không được tính là một stroke của digit nếu nó kéo
dài ra ngoài bbox của rule text.

## 9. Kết quả trả về

Phân biệt hai chế độ output:

### Chế độ nhãn tương thích test_labels.json

Mỗi lần skill giải xong một ảnh hợp lệ, phải tạo record JSON và tự động upsert
vào test_labels.json. Khi user yêu cầu hiển thị JSON, tạo nhãn, xử lý batch hoặc
định dạng giống test_labels.json, output phải parse được và khớp schema thật tại
C:\Users\nguye\Downloads\images_bgonly\test_labels.json:

- Top-level luôn là một array, kể cả chỉ có một ảnh. Mỗi phần tử là một record.
- Thứ tự field chuẩn trong record: filename, raw_top_string, rules,
  final_output; chỉ thêm reviewed ở cuối khi có xác nhận duyệt tay rõ ràng.
- filename là basename PNG dạng string, không phải full path.
- raw_top_string là string dài 5 hoặc 6 và giữ nguyên case quan sát được.
- rules là object có đúng 5 key duy nhất. Key chữ cái luôn lowercase; key
  special giữ nguyên một trong #, $, %, &, @. Mỗi value là một digit được lưu
  dưới dạng string, không phải number.
- final_output là string digit dài đúng bằng raw_top_string và được tính bằng
  lookup không phân biệt case cho chữ cái.
- reviewed là field tùy chọn. Chỉ ghi đúng boolean true sau khi user hoặc nguồn
  review đáng tin đã xác nhận record; record chưa review phải bỏ hẳn field này,
  không ghi false, null hoặc string "true".
- Không thêm confidence, candidates, angle, anchors, notes, reasoning, full
  path hay field phụ nào vào record. Các dữ kiện review nằm ngoài label JSON.
- Dùng indent 2 spaces, dấu ngoặc kép JSON, không comment, không trailing comma,
  không Markdown fence và kết thúc file/output bằng newline khi ghi ra file.
- Thứ tự key bên trong rules không mang nghĩa. Giữ thứ tự đọc được hoặc một thứ
  tự ổn định; không dùng vị trí key để tính final_output.

Ví dụ canonical một record chưa review:

    [
      {
        "filename": "auto_1783134791_91ae2a5c44.png",
        "raw_top_string": "#DHENE",
        "rules": {
          "#": "3",
          "d": "5",
          "e": "1",
          "h": "9",
          "n": "7"
        },
        "final_output": "359171"
      }
    ]

Ví dụ chỉ minh họa schema; không sao chép filename, nhãn hoặc rules của nó sang
ảnh khác. Với batch, giữ thứ tự ảnh đầu vào và không tạo hai record cùng
filename.

Mỗi ảnh đọc được và có đủ 5 rules cùng 5-6 slot phải tạo đúng một record. Nếu
ambiguity ảnh hưởng digit, chọn best candidate sau toàn bộ kiểm tra làm dự đoán
provisional, tính final_output từ dự đoán đó và vẫn ghi record nhưng bỏ field
reviewed. Không nhét null, candidate array hay chẩn đoán vào schema; ghi
candidate thay thế và nguyên nhân vào báo cáo review riêng để user/app đối chiếu.

### Chế độ báo cáo review cho người đọc

Không yêu cầu hoặc hiển thị chain-of-thought. Trả các dữ kiện có thể kiểm tra:

- Rules: năm mapping đã đọc.
- Raw top string: chuỗi 5-6 ký tự, giữ case.
- Final output: chuỗi digit sau mapping không phân biệt case.
- Confidence: high, medium hoặc low; nếu không high, nêu vị trí và tối đa hai
  ứng viên trong rules cùng neo còn thiếu.
- Với slot đã xoay, bị che hoặc không high: ghi góc chuẩn hóa ước lượng, các neo
  observed, neo plausibly occluded bởi màu/line nào, neo unexplained missing,
  hai candidate tốt nhất và confidence. Đây là dữ kiện kiểm chứng, không phải
  chain-of-thought.

Đánh dấu final_output là high confidence trong báo cáo chỉ khi:

- cả 5 rules đều high confidence; và
- mọi vị trí top có identity high confidence; hoặc phần chưa chắc chỉ là case
  của cùng một chữ và hai case ánh xạ cùng digit.

Nếu ambiguity còn liên quan tới hai key có digit khác nhau, vẫn ghi final_output
provisional từ candidate đứng đầu, đồng thời trả tối đa hai candidates và không
mô tả dự đoán đó là chắc chắn.

Nếu user chỉ hỏi final output, hoàn tất upsert trước rồi có thể trả final_output
trước, kèm raw top và rules ngắn gọn để kiểm chứng.

## 10. Ghi JSON an toàn

Sau mỗi ảnh giải xong, tự động ghi vào
C:\Users\nguye\Downloads\images_bgonly\test_labels.json. Đây là hành vi mặc
định của skill và không chờ hỏi lại quyền ghi. Nếu user chỉ định thêm một output
khác, vẫn upsert test_labels.json rồi mới ghi bản sao/phụ; output khác không thay
thế file nhãn chính.

Quy trình upsert bắt buộc:

- Ghi record hoặc batch record dự kiến vào một JSON tạm bằng serializer, không
  truyền raw string qua shell interpolation.
- Bắt buộc gọi validator/upserter, không tự append bằng thao tác text:

      python scripts/validate_and_upsert.py --record-file <record-json-tạm>

- Script kiểm tra schema, top alphabet, rule alphabet, đúng 5 mappings, mapping
  coverage, digit-only final output, duplicate filename và xung đột với
  review_labels.json trước khi atomic replace.
- Nếu script trả exit khác 0, không bypass, không sửa validator và không ghi thủ
  công. Quay lại rules/top, sửa dự đoán rồi chạy lại.
- Kết quả AI luôn bỏ field reviewed. Filename có trong review_labels.json được
  bảo vệ như ground truth kể cả test record hiện tại chưa có reviewed: true.
- Sau mỗi ảnh hoặc batch nhỏ, đọc lại file và tính lại output từ record vừa ghi.
- Chỉ không ghi cho file ảnh hỏng/không đọc được, rules không đủ 5 mapping hoặc
  không thể tạo chuỗi 5-6 slot hợp lệ. Báo lỗi cụ thể; không chế null để lấp schema.

Bảo toàn ký tự đặc biệt:

- Không truyền raw_top_string hoặc rule keys trực tiếp qua chuỗi shell có nội
  suy. PowerShell có thể mở rộng $, CMD có thể mở rộng %, và các shell khác có
  quy tắc riêng.
- Ưu tiên file JSON, stdin có cấu trúc hoặc API; để serializer giữ nguyên đúng
  năm special #, $, %, &, @.
- Mapping lowercase chỉ áp dụng cho chữ cái; không thay đổi special characters.

## 11. Xử lý nhiều ảnh

- Chỉ chọn exemplar từ review_labels.json qua chỉ mục reviewed; test-only record
  không được dùng làm mẫu hình thái.
- Dùng batch đủ nhỏ để mỗi ảnh còn đọc được; đánh số filename và nhãn rõ ràng.
- Nếu dùng grid, giữ số tile vừa phải và độ phân giải đủ để nhìn từng pixel.
- Chỉ ghi kết quả cho ảnh thực sự đã được công cụ trả về.
- Không copy kết quả giữa batch; đối chiếu filename ngay trước khi upsert.
- Sau mỗi ảnh hoặc batch nhỏ, upsert và verify test_labels.json ngay để có thể
  tiếp tục an toàn nếu phiên bị gián đoạn.
- Khi có thể, dùng ảnh chứa nhiều lần cùng một ký tự hoặc hai ký tự dễ nhầm
  trong cùng một ảnh, vì noise/font/scale khi đó được kiểm soát tốt hơn.

## 12. Checklist cuối

- [ ] Đã mở đúng ảnh và đúng filename.
- [ ] Đã đọc đủ 5 rules.
- [ ] Đã giữ raw case và chỉ normalize case lúc mapping.
- [ ] Đã loại G/g, I/i, J/j, O/o, Y/y khỏi raw top; i/o/y rule-only distractor
      vẫn được giữ khi thật sự có trong đủ 5 mappings.
- [ ] Đã loại mọi special ngoài #, $, %, &, @ khỏi rules, top và output.
- [ ] Đã tách noise dài khỏi component trước khi lấy thứ tự x.
- [ ] Đã nhận dạng top trên từng layer màu sau khi loại line 1 px, không theo
      ấn tượng từ composite/raw.
- [ ] ROI nghiêng đã được dựng đứng bằng xoay trái/phải cục bộ; không mirror,
      lật hoặc thử 90°/180°.
- [ ] Đã tách riêng nét quan sát, nét có thể bị che và nét thiếu không giải
      thích được; không tự vẽ hoàn chỉnh glyph.
- [ ] Anchor ledger và occlusion map đã được áp dụng cho mọi candidate ở mọi
      slot, không chỉ cho ví dụ P hoặc một nhóm chữ dễ nhầm.
- [ ] Case được đo trên canonical envelope so với cap/x-height và baseline.
- [ ] Đã tìm đủ 5 hoặc 6 glyph, không đếm fragment thành glyph mới.
- [ ] Đã lập inventory panel-local x trên top sheet một cột; không sắp theo
      sheet column, palette hoặc global sheet x.
- [ ] Mỗi glyph high có ít nhất hai neo; glyph chưa đủ neo đã được ghi
      provisional với tối đa hai candidates trong báo cáo và vẫn thuộc rules.
- [ ] Mọi raw char có mapping.
- [ ] final_output dài bằng raw_top_string và đã được tính lại.
- [ ] Nếu xuất nhãn JSON, top-level là array và mỗi record đúng thứ tự/schema
      filename, raw_top_string, rules, final_output, reviewed tùy chọn.
- [ ] rules có đúng 5 key, letter key lowercase, digit value là string; record
      chưa được duyệt không chứa field reviewed.
- [ ] JSON nhãn không chứa confidence, angle, candidates, anchors hoặc field phụ.
- [ ] Nếu còn mơ hồ, đã ghi best candidate provisional, bỏ reviewed và nêu vị
      trí/candidates trong báo cáo riêng.
- [ ] Record đã vượt scripts/validate_and_upsert.py; không bypass validator.
- [ ] Đã đọc lại test_labels.json để verify; không tạo filename trùng hoặc ghi đè
      ground truth trong review_labels.json.
