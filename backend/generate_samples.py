"""Generate realistic Chinese multi-page PDFs, images, and copy valid MP3/MP4 samples."""
import math
import shutil
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "sample_assets"
FONT_BOLD = "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc"


def get_font(size: int):
    try:
        return ImageFont.truetype(FONT_BOLD, size)
    except Exception:
        return ImageFont.load_default()


def make_pdf(filename: str, pages_spec: list[dict]):
    images = []
    W, H = 1240, 1754  # A4 at 150 DPI
    for idx, spec in enumerate(pages_spec, 1):
        img = Image.new("RGB", (W, H), "#FFFFFF")
        draw = ImageDraw.Draw(img)
        # Header bar
        draw.rectangle([0, 0, W, 130], fill=spec.get("accent", "#1976D2"))
        draw.text((64, 30), spec["subject"], fill="#DCEEFF", font=get_font(28))
        draw.text((64, 68), spec["title"], fill="#FFFFFF", font=get_font(40))
        draw.text((W - 220, 48), f"第 {idx} / {len(pages_spec)} 页", fill="#FFFFFF", font=get_font(28))

        # Subtitle band
        draw.rectangle([64, 155, W - 64, 215], fill="#F1F6FB", outline="#D4E3F3", width=2)
        draw.text((88, 170), spec["subtitle"], fill="#1E3A5F", font=get_font(26))

        # Body paragraphs
        y = 250
        for block in spec["blocks"]:
            kind = block.get("kind", "text")
            if kind == "heading":
                draw.rectangle([64, y + 6, 74, y + 42], fill=spec.get("accent", "#1976D2"))
                draw.text((92, y), block["text"], fill="#1A2B3C", font=get_font(34))
                y += 62
            elif kind == "box":
                lines = block["lines"]
                box_h = 36 + len(lines) * 44
                draw.rounded_rectangle([64, y, W - 64, y + box_h], radius=12, fill="#FAFAFA", outline="#CFD8DC", width=2)
                by = y + 18
                for ln in lines:
                    draw.text((92, by), ln, fill="#263238", font=get_font(27))
                    by += 44
                y += box_h + 28
            elif kind == "parabola":
                cx, cy = W // 2, y + 210
                draw.rectangle([140, y, W - 140, y + 400], fill="#FAFCFF", outline="#B0BEC5", width=2)
                # Grid
                for gx in range(180, W - 140, 60):
                    draw.line([(gx, y), (gx, y + 400)], fill="#E3EDF5", width=1)
                for gy in range(y + 40, y + 400, 50):
                    draw.line([(140, gy), (W - 140, gy)], fill="#E3EDF5", width=1)
                # Axes
                draw.line([(160, cy + 80), (W - 160, cy + 80)], fill="#37474F", width=3)
                draw.line([(cx, y + 20), (cx, y + 380)], fill="#37474F", width=3)
                # Parabola y = 0.5*(x-1)^2 - 1
                pts = []
                for px in range(-280, 281, 4):
                    x_val = px / 65.0
                    y_val = 0.45 * (x_val - 0.6) ** 2 - 1.2
                    py = int((cy + 80) - y_val * 65)
                    if y + 20 <= py <= y + 380:
                        pts.append((cx + px, py))
                if len(pts) > 1:
                    draw.line(pts, fill="#1976D2", width=5)
                draw.text((cx + 120, y + 40), "y = a(x - h)² + k (a > 0)", fill="#1976D2", font=get_font(28))
                y += 430
            else:
                for ln in block["lines"]:
                    draw.text((72, y), ln, fill="#333333", font=get_font(28))
                    y += 46
                y += 18

        # Footer
        draw.line([(64, H - 100), (W - 64, H - 100)], fill="#CFD8DC", width=2)
        draw.text((64, H - 80), "少年派智慧课堂 · 自主学习电子讲义", fill="#78909C", font=get_font(24))
        draw.text((W - 320, H - 80), f"Page {idx}", fill="#78909C", font=get_font(24))
        images.append(img)

    out_path = OUT / filename
    images[0].save(out_path, "PDF", resolution=150.0, save_all=True, append_images=images[1:])
    print(f"Generated PDF: {out_path} ({out_path.stat().st_size} bytes, {len(images)} pages)")


def make_board_image(filename: str):
    img = Image.new("RGB", (1024, 640), "#1B4332")
    draw = ImageDraw.Draw(img)
    draw.rectangle([16, 16, 1008, 624], outline="#D8F3DC", width=4)
    draw.text((60, 48), "《岳阳楼记》结构板书", fill="#FFFFFF", font=get_font(42))
    draw.line([(60, 112), (960, 112)], fill="#95D5B2", width=3)
    items = [
        ("一、作记缘由（第1段）", "滕子京谪守巴陵郡 → 政通人和，百废具兴 → 重修岳阳楼"),
        ("二、洞庭全景（第2段）", "衔远山，吞长江，浩浩汤汤，横无际涯 → 迁客骚人览物之情"),
        ("三、一阴一晴（第3-4段）", "霪雨霏霏（去国怀乡，感极而悲） vs 春和景明（心旷神怡，其喜洋洋）"),
        ("四、古仁人之心（第5段）", "不以物喜，不以己悲 → 先天下之忧而忧，后天下之乐而乐"),
    ]
    y = 148
    for title, desc in items:
        draw.text((70, y), title, fill="#FFE082", font=get_font(30))
        draw.text((100, y + 44), desc, fill="#F1FAEE", font=get_font(25))
        y += 110
    out_path = OUT / filename
    img.save(out_path, "PNG")
    print(f"Generated PNG: {out_path} ({out_path.stat().st_size} bytes)")


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    make_pdf("岳阳楼记_课堂讲义.pdf", [
        {
            "subject": "语文 · 九年级上册 · 第三单元",
            "title": "《岳阳楼记》精读讲义",
            "subtitle": "授课教师：陈老师  |  发布日期：2021-09-30  |  建议用时：25 分钟",
            "accent": "#1976D2",
            "blocks": [
                {"kind": "heading", "text": "一、学习目标"},
                {"kind": "box", "lines": [
                    "1. 掌握“谪、属、衔、汤汤、薄暮、景明”等重点文言实词与虚词。",
                    "2. 体会本文叙事、写景、抒情、议论紧密结合的写作特色。",
                    "3. 理解范仲淹“先天下之忧而忧，后天下之乐而乐”的旷达胸襟。",
                ]},
                {"kind": "heading", "text": "二、核心段落原文节选"},
                {"kind": "text", "lines": [
                    "    庆历四年春，滕子京谪守巴陵郡。越明年，政通人和，百废具兴，",
                    "乃重修岳阳楼，增其旧制，刻唐贤今人诗赋于其上，属予作文以记之。",
                    "    予观夫巴陵胜状，在洞庭一湖。衔远山，吞长江，浩浩汤汤，",
                    "横无际涯，朝晖夕阴，气象万千，此则岳阳楼之大观也。",
                ]},
                {"kind": "heading", "text": "三、重点字词注音与释义"},
                {"kind": "box", "lines": [
                    "• 谪守（zhé）：因罪贬谪流放，出任外官。",
                    "• 属予作文（zhǔ）：同“嘱”，嘱托。",
                    "• 浩浩汤汤（shāng）：水势浩大的样子。",
                    "• 薄暮冥冥（bó）：迫近傍晚，天色昏暗。",
                ]},
            ],
        },
        {
            "subject": "语文 · 九年级上册 · 第三单元",
            "title": "《岳阳楼记》景物对比与主旨赏析",
            "subtitle": "对照阅读：迁客骚人之情 vs 古仁人之心",
            "accent": "#1976D2",
            "blocks": [
                {"kind": "heading", "text": "四、写景对比梳理"},
                {"kind": "box", "lines": [
                    "【阴雨图】霪雨霏霏，连月不开，阴风怒号，浊浪排空；日星隐曜，山岳潜形。",
                    "   → 情感：去国怀乡，忧谗畏讥，满目萧然，感极而悲者矣。",
                    "",
                    "【晴明图】春和景明，波澜不惊，上下天光，一碧万顷；沙鸥翔集，锦鳞游泳。",
                    "   → 情感：心旷神怡，宠辱偕忘，把酒临风，其喜洋洋者矣。",
                ]},
                {"kind": "heading", "text": "五、课后思考与随堂小练"},
                {"kind": "text", "lines": [
                    "1. 作者写“迁客骚人”一悲一喜两种截然不同的览物之情，用意何在？",
                    "   提示：以迁客骚人“以物喜、以己悲”的世俗心态，反衬“古仁人”",
                    "   “不以物喜，不以己悲”的崇高境界。",
                    "",
                    "2. 请结合配套音频《名家范读.mp3》跟读第 3–5 段，注意骈散交替的节奏。",
                ]},
            ],
        },
        {
            "subject": "语文 · 九年级上册 · 第三单元",
            "title": "《岳阳楼记》随堂默写检测",
            "subtitle": "请独立完成以下名句默写，完成后在任务页核对答案",
            "accent": "#1976D2",
            "blocks": [
                {"kind": "heading", "text": "六、理解性默写（每空 2 分，共 10 分）"},
                {"kind": "box", "lines": [
                    "1. 表达作者旷达胸襟的句子：________________，________________。",
                    "2. 动静结合描写洞庭湖月夜美景：________________，________________。",
                    "3. 抒发作者远大政治抱负的千古名句：________________，________________。",
                    "4. 从时间角度表现洞庭湖早晚景色变幻：________________，________________。",
                    "5. 概括滕子京政绩的八个字：________________，________________。",
                ]},
                {"kind": "heading", "text": "七、拓展延伸"},
                {"kind": "text", "lines": [
                    "结合本课配套视频《课堂朗诵示范视频.mp4》，尝试背诵全文并录音提交。",
                ]},
            ],
        },
    ])

    make_pdf("古诗文默写与基础练习.pdf", [
        {
            "subject": "语文 · 阶段巩固",
            "title": "第三单元古诗文默写与基础练习",
            "subtitle": "覆盖篇目：《岳阳楼记》《醉翁亭记》《湖心亭看雪》",
            "accent": "#00897B",
            "blocks": [
                {"kind": "heading", "text": "一、字词注音选择"},
                {"kind": "box", "lines": [
                    "1. 下列加点字注音完全正确的一项是（ ）",
                    "   A. 伛偻(lǚ)   觥筹(gōng)   阴翳(yì)    雾凇沆砀(hàng)",
                    "   B. 谪守(zhé)  岸芷汀兰(dīng) 绮户(qǐ)  毳衣炉火(cuì)",
                ]},
                {"kind": "heading", "text": "二、名句默写"},
                {"kind": "text", "lines": [
                    "1. 醉翁之意不在酒，____________________。（欧阳修《醉翁亭记》）",
                    "2. 雾凇沆砀，天与云与山与水，____________________。（张岱《湖心亭看雪》）",
                    "3. 但愿人长久，____________________。（苏轼《水调歌头》）",
                ]},
            ],
        },
        {
            "subject": "语文 · 阶段巩固",
            "title": "参考答案与解析",
            "subtitle": "请先独立作答，再翻至此页核对",
            "accent": "#00897B",
            "blocks": [
                {"kind": "heading", "text": "参考答案"},
                {"kind": "box", "lines": [
                    "一、1. A（B 项“汀”应读 tīng）",
                    "二、1. 在乎山水之间也   2. 上下一白   3. 千里共婵娟",
                ]},
            ],
        },
    ])

    make_pdf("二次函数图像与性质专题.pdf", [
        {
            "subject": "数学 · 九年级上册 · 第二十二章",
            "title": "二次函数图像与性质专题讲义",
            "subtitle": "授课教师：李老师  |  核心考点：顶点式、对称轴、最值与平移",
            "accent": "#3F51B5",
            "blocks": [
                {"kind": "heading", "text": "一、二次函数图像示意"},
                {"kind": "parabola"},
                {"kind": "heading", "text": "二、核心性质总结"},
                {"kind": "box", "lines": [
                    "1. 顶点式：y = a(x - h)² + k (a ≠ 0)，顶点坐标 (h, k)，对称轴 x = h。",
                    "2. 当 a > 0 时开口向上，x = h 时取最小值 k；当 a < 0 时开口向下。",
                    "3. 平移规律：左加右减（针对 x），上加下减（针对常数项 k）。",
                ]},
            ],
        },
        {
            "subject": "数学 · 九年级上册 · 第二十二章",
            "title": "典例精析与变式训练",
            "subtitle": "例题 1–2 请在草稿纸上完整写出配方过程",
            "accent": "#3F51B5",
            "blocks": [
                {"kind": "heading", "text": "三、典型例题"},
                {"kind": "box", "lines": [
                    "例 1：已知抛物线 y = x² - 4x + 3。",
                    "  (1) 求顶点坐标及对称轴；",
                    "  (2) 当 0 ≤ x ≤ 3 时，求函数 y 的取值范围。",
                    "  解：y = (x - 2)² - 1，顶点 (2, -1)，当 x=2 时 y_min=-1，x=0 时 y_max=3。",
                ]},
            ],
        },
    ])

    make_pdf("Unit3_Reading_Guide.pdf", [
        {
            "subject": "英语 · 九年级上册 · Unit 3",
            "title": "Unit 3 Could you please tell me where the restrooms are?",
            "subtitle": "Teacher: Ms. Wang  |  Grammar Focus: Polite Requests & Objective Clauses",
            "accent": "#00ACC1",
            "blocks": [
                {"kind": "heading", "text": "Part 1: Key Expressions"},
                {"kind": "box", "lines": [
                    "• Excuse me, could you please tell me how to get to the bookstore?",
                    "• Do you know when the science museum closes today?",
                    "• Pardon me, I wonder if there is a post office nearby.",
                ]},
                {"kind": "heading", "text": "Part 2: Reading Strategy"},
                {"kind": "text", "lines": [
                    "1. Pay attention to the topic sentence of each paragraph.",
                    "2. Compare direct questions vs. polite requests in different contexts.",
                    "3. Listen to the attached MP3 track twice and shadow-read aloud.",
                ]},
            ],
        },
    ])

    make_board_image("岳阳楼记_课堂板书.png")

    # Copy valid playable MP3 and MP4 from 10_mock_backend.zip
    src10 = Path("/tmp/drive_archive/10/assets")
    if (src10 / "听力测试音.mp3").exists():
        shutil.copy2(src10 / "听力测试音.mp3", OUT / "岳阳楼记_名家范读.mp3")
        shutil.copy2(src10 / "听力测试音.mp3", OUT / "Unit3_听力训练.mp3")
        print("Copied MP3 samples")
    if (src10 / "视频测试片.mp4").exists():
        shutil.copy2(src10 / "视频测试片.mp4", OUT / "课堂朗诵示范视频.mp4")
        shutil.copy2(src10 / "视频测试片.mp4", OUT / "二次函数动态演示.mp4")
        print("Copied MP4 samples")


if __name__ == "__main__":
    main()
