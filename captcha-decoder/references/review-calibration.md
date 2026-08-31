# Calibration từ nhãn đã review

Nguồn ground truth bắt buộc là
`C:\Users\nguye\Downloads\images_bgonly\review_labels.json`. Không dùng record
chỉ có trong `test_labels.json` để học font, vì file đó chứa dự đoán AI chưa duyệt.

## Audit snapshot 2026-08-26

- `test_labels.json`: 704 record.
- `review_labels.json`: 354 record, toàn bộ có `reviewed: true` và đều nằm trong
  `test_labels.json`.
- Chỉ 109 trong 354 record trùng hoàn toàn; 245 record lệch ít nhất một field.
- 243 chuỗi top khác nhau, 33 bảng rules khác nhau và 243 final output khác nhau.
- 61 cặp có độ dài top khác nhau; 42 cặp có cùng multiset glyph nhưng sai thứ tự.
- 50 record test sinh ký tự ngoài alphabet top; 97 final output còn chứa chữ hoặc
  special thay vì chỉ digit.
- 154 record bị validator hiện tại loại ngay. Trong 200 record vượt validator,
  vẫn còn 91 record sai về identity, case, slot count hoặc thứ tự.

Các con số là snapshot để giải thích failure mode, không phải hằng số. Mỗi lần
audit mới phải đọc lại hai file.

## Nguồn ưu tiên

1. Sửa tay mới nhất của người dùng.
2. Record trong `review_labels.json`.
3. Exemplar được dẫn từ `references/labeled-exemplars.md`, nhưng chỉ khi filename
   vẫn tồn tại và nhãn vẫn trùng `review_labels.json`.
4. Dự đoán chưa review trong `test_labels.json` chỉ là dữ liệu cần kiểm tra, không
   phải bằng chứng hình thái.

Khi cùng filename lệch giữa hai file, lấy đủ `raw_top_string`, `rules` và
`final_output` từ review record. Không trộn field của hai nguồn.

## Failure cases đã xem trực tiếp

| Filename | Test sai | Review chuẩn | Bài học |
|---|---|---|---|
| auto_1783136086_ca22d1f0a9.png | `a/oUZu` | `%UZZU` | `%` là một envelope ba neo; không tách loop và slash thành nhiều ký tự. |
| auto_1783136096_cf0c0aaa03.png | `f&PH&@` | `P&FH&@` | Dùng rule set và topology P/F; order phải theo panel-local x, không theo sheet. |
| auto_1783136097_9d7eb8415b.png | `qOCkco` | `QQCXCQ` | O bị cấm ở top; khóa candidates vào rules và giữ case theo canonical height. |
| auto_1783136106_cf534cff59.png | `xsOss` | `xsDss` | D có left stem cùng bowl toàn chiều cao; vòng tròn không được xuất O. |
| auto_1783136119_545e1deeb3.png | `qDpkFL` | `qDfkFL` | p không có trong rules; f giữ ascender/crossbar dù bị line che. |
| auto_1783136129_47b8d0f2b2.png | `BMdF8%` | `BnFFB%` | Không xuất digit ở top; n có một shoulder, B có spine và hai bowl. |
| auto_1783136130_b8c45bc03c.png | `vpNNQ` | `VVVVQ` | Bốn glyph đồng dạng qua nhiều màu phải được cluster là duplicate V. |
| auto_1783136142_ce717d2cac.png | `s%` | `$S$PP` | Không phát hành trước khi inventory có đủ 5 hoặc 6 slot. |
| auto_1783136267_fe0de260ad.png | `*W%#Xx` | `#W##Xx` | `*` không hợp lệ; # cần hai stem và hai crossbar trong cùng envelope. |
| auto_1783136756_569d44e3b5.png | `UUU#UU` | `UU#UUU` | Sheet hai cột đã làm AI đọc cột trái trước; top sheets phải căn một cột. |

## Bẫy thứ tự do sheet

Phiên bản helper cũ đặt top masks thành hai cột. Với ảnh
`auto_1783136756_569d44e3b5.png`, đọc theo panel cột trái rồi cột phải tạo
`UUU#UU`, trong khi sắp theo x cục bộ chung 0..127 tạo đúng `UU#UUU`.

- Chỉ dùng top sheet một cột do helper hiện tại tạo.
- Tọa độ dùng để sắp là panel-local x trong ảnh gốc, không phải global x của PNG
  sheet và không phải thứ tự màu/panel.
- Lập inventory `[color, anchor_x, candidate, confidence]`, rồi sắp `anchor_x`
  trước khi ghép chuỗi.
- Với glyph xoay làm bbox vượt qua glyph bên cạnh, ước lượng canonical placement
  anchor bằng stem, symmetry axis hoặc baseline intercept sau khi dựng đứng; không
  dùng raw x-min.

## Closed-set và validator

Trước khi ghi, record phải vượt `scripts/validate_and_upsert.py`:

- raw top dài 5 hoặc 6;
- đúng 5 rules;
- mỗi raw character có normalized key trong rules;
- top không chứa digit, dấu câu lạ hoặc G/I/J/O/Y;
- final output chỉ có digit và bằng phép mapping tính lại;
- không có field ngoài schema;
- không xung đột ground truth trong review file.

Nếu validator từ chối, quay lại ảnh và sửa dự đoán. Không sửa validator hoặc bỏ
qua lỗi để ép ghi.

## Alphabet top khác alphabet rule

Ground truth review hiện không có G/g, I/i, J/j, O/o hoặc Y/y trong
`raw_top_string`. Tuy nhiên rule table có các distractor không dùng ở top:

- `o` tại `auto_1783136323_e6111ae2c3.png`;
- `i` tại `auto_1783136420_c738867e3f.png`;
- `y` tại `auto_1783136529_45b2914090.png` và
  `auto_1783136610_ed7b88a134.png`.

Vì vậy phải đọc đủ năm rule keys như nhìn thấy và normalize letter key sang
lowercase, kể cả i/o/y. Nhưng khi tạo top candidate set, loại i/o/y cùng g/j.
Trong review hiện tại không có g hoặc j ở cả top lẫn rules.

## Quy tắc global-consistency

- Cluster các ROI canonical gần đồng dạng trước khi gán identity. Nếu một ảnh có
  ba hoặc bốn bản sao cùng topology, mặc định chúng cùng ký tự cho đến khi có neo
  loại trừ rõ; rotation và màu không phải neo loại trừ.
- Giải identity và order thành hai bước riêng. Không đổi tên glyph để làm thứ tự
  trông hợp lý, và không đổi thứ tự để dùng hết rule keys.
- Sau khi có inventory 5 hoặc 6 slot, chạy closed-set veto trên từng slot rồi mới
  ghép raw string. Một ký tự ngoài rules làm toàn record invalid.
- Case được quyết định bằng canonical height so với cap-height/x-height của cùng
  ảnh. Không lowercase top chỉ vì rule keys được lưu lowercase.
