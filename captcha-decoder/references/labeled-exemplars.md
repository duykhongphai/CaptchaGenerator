# Reviewed exemplar index

Mọi exemplar trong chỉ mục này lấy từ ground truth
`C:\Users\nguye\Downloads\images_bgonly\review_labels.json`. Không dùng
`test_labels.json` làm exemplar bank vì record chưa review có thể sai nặng.

## Cách dùng

1. Trước khi dùng một filename, đọc lại record của nó trong review file và xác
   nhận `raw_top_string` vẫn trùng chuỗi ghi dưới đây.
2. Chạy `scripts/prepare_layers.py` cho target và exemplar; nhìn top residual
   một cột trước, raw mask sau.
3. So cùng case theo topology và canonical orientation, không so màu hoặc sheet
   column.
4. Với ký tự dễ nhầm, dùng hai exemplar. Exemplar có ba hoặc nhiều glyph lặp
   giúp tách stroke bền vững khỏi occlusion và rotation.
5. Với rule key, mở rule residual của record có đúng key cần kiểm tra.
6. Nếu target đã có trong review file, record đó là ground truth, không phải một
   blind prediction.

## Special

| Glyph | Exemplar 1 | Exemplar 2 | Exemplar 3 |
|---|---|---|---|
| # | auto_1783135975_f9a080fb30.png — E%### | auto_1783136171_c0c15e6361.png — ##ll# | auto_1783136267_fe0de260ad.png — #W##Xx |
| $ | auto_1783136301_378da7f287.png — Q$$$$ | auto_1783135876_92699ad905.png — $$$Z&Q | auto_1783136554_950fd242b9.png — pD$@$$ |
| % | auto_1783135848_e7bfaa2187.png — %%H%B% | auto_1783135982_2b97742d68.png — LD%%D% | auto_1783136008_5338d15f90.png — %C%%A |
| & | auto_1783136031_26be3dcdad.png — &&&U&$ | auto_1783135847_94290f41de.png — &&&UH | auto_1783135915_08f636788c.png — HB&B&& |
| @ | auto_1783136986_f1d6042106.png — @U@@# | auto_1783135857_65ad315423.png — $@SS$@ | auto_1783135978_3e312bbdd1.png — QZ@@X |

## Chữ hoa top

| Glyph | Exemplar ưu tiên | Exemplar phụ |
|---|---|---|
| A | auto_1783136705_f4e91ec844.png — A%AALA | auto_1783135892_023d3aea95.png — AKDAA |
| B | auto_1783135958_835ddeb171.png — BDBB@N | auto_1783135986_5aa0eb32e7.png — BSDBBD |
| C | auto_1783136759_3764590364.png — XCCLCC | auto_1783136038_b89b927c9a.png — C#CDDC |
| D | auto_1783136610_ed7b88a134.png — DDDFDT | auto_1783136133_a0deb2c400.png — FDNDSD |
| E | auto_1783136420_c738867e3f.png — EE%SEE | auto_1783135892_ef5097816d.png — EEEKA |
| F | auto_1783136815_765d088f9a.png — $FFFF$ | auto_1783135847_ce3a1d7a02.png — FFA#AF |
| H | auto_1783136230_e128a637b4.png — FHHHH | auto_1783136727_4940db0748.png — HHHZHS |
| K | auto_1783136973_1eb6ac1e37.png — HKKAKK | auto_1783136812_f56b2dc3a3.png — KKFKPV |
| L | auto_1783135878_f796ba8875.png — LLDLN | auto_1783136307_4c7507248b.png — $z%LLL |
| N | auto_1783136058_3b1e06482f.png — NNSFNN | auto_1783135982_0fef9e372d.png — $N&NN |
| P | auto_1783135982_9abe469f30.png — H&PPP | auto_1783136139_84c53e6fbd.png — PPPZH |
| Q | auto_1783136097_9d7eb8415b.png — QQCXCQ | auto_1783136953_bf39d42df1.png — QQTQXA |
| S | auto_1783136660_095aa2f866.png — SE&SES | auto_1783136948_3a4d2d8f22.png — QS%SS |
| T | auto_1783135956_1a4541bde2.png — @TTTT | auto_1783135913_a090dd4020.png — TTPTS |
| U | auto_1783136756_569d44e3b5.png — UU#UUU | auto_1783135993_ad1896190d.png — UDUULU |
| V | auto_1783136130_b8c45bc03c.png — VVVVQ | auto_1783136046_292ec61e5a.png — &V&VV |
| W | auto_1783136183_f90a31f729.png — WWWPW | auto_1783136814_623ef1de51.png — PWEWWN |
| X | auto_1783136251_7dc69ca405.png — NXNXX | auto_1783136084_a898fac82a.png — cXXPx |
| Z | auto_1783136003_f013a11380.png — ZK%ZZ | auto_1783136051_54adc9a4a3.png — ZZ$SZS |

## Chữ thường top

| Glyph | Exemplar ưu tiên | Exemplar phụ |
|---|---|---|
| a | auto_1783136347_7637885da2.png — @aaHS | auto_1783136120_0df7e48f5d.png — unBaw |
| b | auto_1783136367_9644bfa7ef.png — KbbkK | auto_1783136353_395d29bd09.png — u$buuu |
| c | auto_1783136328_06ed650713.png — scc&&c | auto_1783136606_a696b13dfa.png — HcHcb |
| e | auto_1783136108_88d2335eb6.png — ueueZ | auto_1783136292_7e3878dd4b.png — eEEQq |
| f | auto_1783136239_1b32eb82c6.png — &fLffD | auto_1783136268_2043272844.png — FffV@ |
| h | auto_1783136468_855f258873.png — hS%hQS | auto_1783136152_c56a379794.png — hLSuS |
| k | auto_1783136140_303d2b7f48.png — @HkkW | auto_1783136226_115673ce57.png — NkkKNU |
| l | auto_1783136171_c0c15e6361.png — ##ll# | auto_1783136155_0435b1789c.png — QlqwZL |
| n | auto_1783136120_0df7e48f5d.png — unBaw | auto_1783136129_47b8d0f2b2.png — BnFFB% |
| p | auto_1783136062_bbf4a34dd4.png — vpDp$ | auto_1783136178_2d127a76e6.png — #p&$# |
| q | auto_1783136322_1cafa2cb9e.png — Qqsqqs | auto_1783136382_1eb059cb82.png — hq$$qK |
| s | auto_1783136106_cf534cff59.png — xsDss | auto_1783136322_1cafa2cb9e.png — Qqsqqs |
| t | auto_1783136076_c462836378.png — t$ATt | auto_1783136081_a6d0e6e858.png — NEt%% |
| u | auto_1783136353_395d29bd09.png — u$buuu | auto_1783136108_88d2335eb6.png — ueueZ |
| v | auto_1783136271_aa8cec050d.png — vULvvU | auto_1783136179_3c393a7410.png — x&xvqv |
| w | auto_1783136494_091e92c512.png — Dw&VVw | auto_1783136120_0df7e48f5d.png — unBaw |
| x | auto_1783136179_3c393a7410.png — x&xvqv | auto_1783136262_cea6231331.png — xX%%x |
| z | auto_1783136155_213514f42a.png — Buzzfu | auto_1783136268_9e4dcdc799.png — scSzCz |

## Rule-only distractor exemplars

Các key sau có trong bảng rules đã review nhưng không xuất hiện ở top review:

| Rule key | Exemplar | Ghi chú |
|---|---|---|
| i | auto_1783136420_c738867e3f.png | Rule `i -> 3`; glyph rule có thể trông như I hoa nhưng key JSON normalize lowercase. |
| o | auto_1783136323_e6111ae2c3.png | Rule `o -> 7`; không cho phép O/o trong raw top. |
| y | auto_1783136529_45b2914090.png | Rule `y -> 3`; không cho phép Y/y trong raw top. |
| r | auto_1783136301_378da7f287.png | Rule `r -> 7`; R/r được người dùng xác nhận hợp lệ nhưng hiếm. |

## Hợp lệ nhưng chưa có top exemplar review

- Dạng top d/D: D hoa đã có; d thường chưa có trong review hiện tại.
- M/m và R/r: người dùng xác nhận hợp lệ nhưng review hiện tại chưa có ở top.
- Không nâng chi tiết font-specific của các dạng chưa có exemplar lên high chỉ từ
  một ảnh test chưa review.

## Bị loại khỏi raw top

G/g, I/i, J/j, O/o và Y/y không xuất hiện trong `raw_top_string` của ground
truth review. I/i, O/o và Y/y vẫn có thể là rule distractor không dùng ở top;
xem bảng trên.
