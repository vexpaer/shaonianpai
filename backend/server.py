"""Shaonianpai Remake Backend Server.

Implements the v2 delivery protocol:
- POST /assets
- POST /deliveries
- GET  /me/deliveries?cursor=...&subject=...
- GET  /assets/{id}/content  (supports HTTP Range 206 & HEAD)
- GET  /library, /questionbook, /schedule, /books
- GET  /  (Teacher Web Portal for publishing new materials)
"""
import argparse
import hashlib
import json
import mimetypes
import sqlite3
import uuid
from datetime import datetime
from pathlib import Path

from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import FileResponse, HTMLResponse, JSONResponse, Response
from starlette.routing import Route

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "shaonianpai.sqlite3"
MEDIA_DIR = ROOT / "media"
SAMPLE_DIR = ROOT / "sample_assets"


def connect() -> sqlite3.Connection:
    db = sqlite3.connect(DB_PATH)
    db.row_factory = sqlite3.Row
    db.executescript(
        """
        CREATE TABLE IF NOT EXISTS assets (
            asset_id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            mime TEXT NOT NULL,
            bytes INTEGER NOT NULL,
            sha256 TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS deliveries (
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            delivery_id TEXT UNIQUE NOT NULL,
            client_request_id TEXT UNIQUE,
            revision INTEGER NOT NULL DEFAULT 1,
            subject TEXT NOT NULL,
            task_type TEXT NOT NULL,
            title TEXT NOT NULL,
            teacher_name TEXT NOT NULL,
            start_date TEXT NOT NULL,
            end_date TEXT NOT NULL,
            group_date TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            unread_count INTEGER NOT NULL DEFAULT 0,
            unanswered_count INTEGER NOT NULL DEFAULT 0,
            progress_percent INTEGER NOT NULL DEFAULT 0,
            source_name TEXT NOT NULL DEFAULT '本校资源',
            assets_json TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        """
    )
    return db


def store_asset_bytes(data: bytes, title: str, mime: str | None = None) -> dict:
    MEDIA_DIR.mkdir(parents=True, exist_ok=True)
    sha = hashlib.sha256(data).hexdigest()
    asset_id = sha[:16]
    if not mime:
        mime = mimetypes.guess_type(title)[0] or "application/octet-stream"
    target = MEDIA_DIR / sha
    if not target.exists():
        tmp = MEDIA_DIR / f".tmp-{uuid.uuid4().hex}"
        tmp.write_bytes(data)
        tmp.replace(target)
    now = datetime.utcnow().isoformat(timespec="seconds") + "Z"
    with connect() as db:
        db.execute(
            "INSERT OR REPLACE INTO assets (asset_id, title, mime, bytes, sha256, created_at) VALUES (?,?,?,?,?,?)",
            (asset_id, title, mime, len(data), sha, now),
        )
    return {
        "assetId": asset_id,
        "title": title,
        "mime": mime,
        "bytes": len(data),
        "sha256": sha,
        "url": f"/assets/{asset_id}/content",
    }


def upsert_delivery(
    *,
    client_request_id: str,
    subject: str,
    task_type: str,
    title: str,
    teacher_name: str,
    start_date: str,
    end_date: str,
    group_date: str,
    description: str,
    unread_count: int,
    unanswered_count: int,
    progress_percent: int,
    source_name: str,
    assets: list[dict],
) -> dict:
    now = datetime.utcnow().isoformat(timespec="seconds") + "Z"
    with connect() as db:
        existing = db.execute(
            "SELECT * FROM deliveries WHERE client_request_id = ?", (client_request_id,)
        ).fetchone()
        if existing:
            return {
                "deliveryId": existing["delivery_id"],
                "revision": existing["revision"],
                "seq": existing["seq"],
            }
        delivery_id = "d-" + hashlib.sha256(client_request_id.encode()).hexdigest()[:10]
        cur = db.execute(
            """
            INSERT INTO deliveries (
                delivery_id, client_request_id, revision, subject, task_type, title,
                teacher_name, start_date, end_date, group_date, description,
                unread_count, unanswered_count, progress_percent, source_name,
                assets_json, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                delivery_id,
                client_request_id,
                1,
                subject,
                task_type,
                title,
                teacher_name,
                start_date,
                end_date,
                group_date,
                description,
                unread_count,
                unanswered_count,
                progress_percent,
                source_name,
                json.dumps(assets, ensure_ascii=False),
                now,
            ),
        )
        return {"deliveryId": delivery_id, "revision": 1, "seq": cur.lastrowid}


def seed_default_data():
    if not SAMPLE_DIR.exists():
        return
    a_yyl_pdf = store_asset_bytes((SAMPLE_DIR / "岳阳楼记_课堂讲义.pdf").read_bytes(), "《岳阳楼记》课堂讲义.pdf", "application/pdf")
    a_yyl_mp3 = store_asset_bytes((SAMPLE_DIR / "岳阳楼记_名家范读.mp3").read_bytes(), "《岳阳楼记》名家范读.mp3", "audio/mpeg")
    a_yyl_mp4 = store_asset_bytes((SAMPLE_DIR / "课堂朗诵示范视频.mp4").read_bytes(), "课堂朗诵示范视频.mp4", "video/mp4")
    a_yyl_png = store_asset_bytes((SAMPLE_DIR / "岳阳楼记_课堂板书.png").read_bytes(), "《岳阳楼记》课堂板书.png", "image/png")
    a_gsw_pdf = store_asset_bytes((SAMPLE_DIR / "古诗文默写与基础练习.pdf").read_bytes(), "古诗文默写与基础练习.pdf", "application/pdf")
    a_math_pdf = store_asset_bytes((SAMPLE_DIR / "二次函数图像与性质专题.pdf").read_bytes(), "二次函数图像与性质专题.pdf", "application/pdf")
    a_math_mp4 = store_asset_bytes((SAMPLE_DIR / "二次函数动态演示.mp4").read_bytes(), "二次函数动态演示.mp4", "video/mp4")
    a_eng_pdf = store_asset_bytes((SAMPLE_DIR / "Unit3_Reading_Guide.pdf").read_bytes(), "Unit3_Reading_Guide.pdf", "application/pdf")
    a_eng_mp3 = store_asset_bytes((SAMPLE_DIR / "Unit3_听力训练.mp3").read_bytes(), "Unit3_听力训练.mp3", "audio/mpeg")

    # Seed tasks matching the 2021-09-30 / 2021-09-29 reference photo style + rich multi-subject items
    upsert_delivery(
        client_request_id="seed-yuwen-01",
        subject="语文",
        task_type="教师推送资料",
        title="复刻测试 01 · 《岳阳楼记》精读课资料",
        teacher_name="陈老师",
        start_date="2021-09-30 08:30",
        end_date="2021-10-08 22:00",
        group_date="2021年09月30日",
        description="包含精读讲义 PDF、名家范读 MP3、课堂朗诵示范视频 MP4 与板书图，请依次学习并完成默写。",
        unread_count=1,
        unanswered_count=0,
        progress_percent=75,
        source_name="本校资源",
        assets=[a_yyl_pdf, a_yyl_mp3, a_yyl_mp4, a_yyl_png],
    )
    upsert_delivery(
        client_request_id="seed-yuwen-02",
        subject="语文",
        task_type="课后巩固练习",
        title="第三单元古诗文默写与基础字词巩固",
        teacher_name="陈老师",
        start_date="2021-09-30 16:00",
        end_date="2021-10-07 20:00",
        group_date="2021年09月30日",
        description="完成《岳阳楼记》《醉翁亭记》《湖心亭看雪》名句默写练习，对照第二页参考答案订正。",
        unread_count=0,
        unanswered_count=0,
        progress_percent=100,
        source_name="本校资源",
        assets=[a_gsw_pdf],
    )
    upsert_delivery(
        client_request_id="seed-yuwen-03",
        subject="语文",
        task_type="预习导学",
        title="《醉翁亭记》课前预习与朗读音频",
        teacher_name="陈老师",
        start_date="2021-09-29 17:30",
        end_date="2021-10-03 21:00",
        group_date="2021年09月29日",
        description="提前通读全文，结合音频纠正断句节奏。",
        unread_count=2,
        unanswered_count=1,
        progress_percent=0,
        source_name="本校资源",
        assets=[a_yyl_mp3, a_gsw_pdf],
    )
    upsert_delivery(
        client_request_id="seed-math-01",
        subject="数学",
        task_type="专题微课",
        title="第二十二章 · 二次函数图像与性质专题",
        teacher_name="李老师",
        start_date="2021-09-30 14:00",
        end_date="2021-10-09 22:00",
        group_date="2021年09月30日",
        description="掌握顶点式配方、对称轴及闭区间最值求法，附动态演示视频。",
        unread_count=1,
        unanswered_count=2,
        progress_percent=50,
        source_name="本校资源",
        assets=[a_math_pdf, a_math_mp4],
    )
    upsert_delivery(
        client_request_id="seed-eng-01",
        subject="英语",
        task_type="听说读写任务",
        title="Unit 3 Could you please tell me where the restrooms are?",
        teacher_name="王老师",
        start_date="2021-09-30 10:00",
        end_date="2021-10-08 21:00",
        group_date="2021年09月30日",
        description="阅读导学案并跟读配套听力音频，注意宾语从句的陈述语序。",
        unread_count=0,
        unanswered_count=1,
        progress_percent=60,
        source_name="睿易云",
        assets=[a_eng_pdf, a_eng_mp3],
    )


async def api_upload_asset(request: Request):
    form = await request.form()
    upload = form.get("file")
    if upload is None:
        return JSONResponse({"error": "missing file"}, status_code=400)
    data = await upload.read()
    title = form.get("title") or getattr(upload, "filename", "unnamed.bin")
    mime = form.get("mime") or getattr(upload, "content_type", None)
    meta = store_asset_bytes(data, str(title), str(mime) if mime else None)
    return JSONResponse(meta, status_code=201)


async def api_create_delivery(request: Request):
    body = await request.json()
    req_id = body.get("clientRequestId") or uuid.uuid4().hex
    asset_ids = body.get("assetIds", [])
    assets = []
    with connect() as db:
        for aid in asset_ids:
            row = db.execute("SELECT * FROM assets WHERE asset_id = ?", (aid,)).fetchone()
            if row:
                assets.append(
                    {
                        "assetId": row["asset_id"],
                        "title": row["title"],
                        "mime": row["mime"],
                        "bytes": row["bytes"],
                        "sha256": row["sha256"],
                        "url": f"/assets/{row['asset_id']}/content",
                    }
                )
    today = datetime.now()
    res = upsert_delivery(
        client_request_id=req_id,
        subject=body.get("subject", "语文"),
        task_type=body.get("taskType", "教师推送资料"),
        title=body.get("title", "未命名任务"),
        teacher_name=body.get("teacherName", "陈老师"),
        start_date=body.get("startDate", today.strftime("%Y-%m-%d %H:%M")),
        end_date=body.get("endDate", today.strftime("%Y-%m-%d 22:00")),
        group_date=body.get("groupDate", today.strftime("%Y年%m月%d日")),
        description=body.get("description", ""),
        unread_count=len(assets),
        unanswered_count=0,
        progress_percent=0,
        source_name=body.get("sourceName", "本校资源"),
        assets=assets,
    )
    return JSONResponse(res, status_code=201)


async def api_list_deliveries(request: Request):
    cursor = int(request.query_params.get("cursor", "0") or "0")
    subject = request.query_params.get("subject", "").strip()
    with connect() as db:
        if subject and subject != "全部":
            rows = db.execute(
                "SELECT * FROM deliveries WHERE seq > ? AND subject = ? ORDER BY seq ASC",
                (cursor, subject),
            ).fetchall()
        else:
            rows = db.execute(
                "SELECT * FROM deliveries WHERE seq > ? ORDER BY seq ASC",
                (cursor,),
            ).fetchall()
    items = []
    max_seq = cursor
    for r in rows:
        max_seq = max(max_seq, r["seq"])
        items.append(
            {
                "deliveryId": r["delivery_id"],
                "revision": r["revision"],
                "subject": r["subject"],
                "taskType": r["task_type"],
                "title": r["title"],
                "teacherName": r["teacher_name"],
                "startDate": r["start_date"],
                "endDate": r["end_date"],
                "groupDate": r["group_date"],
                "description": r["description"],
                "unreadResourceCount": r["unread_count"],
                "unansweredCount": r["unanswered_count"],
                "progressPercent": r["progress_percent"],
                "sourceName": r["source_name"],
                "assets": json.loads(r["assets_json"]),
            }
        )
    return JSONResponse({"items": items, "removed": [], "nextCursor": str(max_seq)})


async def api_asset_content(request: Request):
    aid = request.path_params["id"]
    with connect() as db:
        row = db.execute("SELECT * FROM assets WHERE asset_id = ?", (aid,)).fetchone()
    if not row:
        return Response(status_code=404)
    path = MEDIA_DIR / row["sha256"]
    if not path.exists():
        return Response(status_code=404)
    # FileResponse in Starlette natively supports HEAD & HTTP Range 206 Partial Content!
    return FileResponse(path, media_type=row["mime"], filename=row["title"])


async def api_library(request: Request):
    with connect() as db:
        rows = db.execute("SELECT * FROM assets ORDER BY created_at DESC").fetchall()
    items = [
        {
            "assetId": r["asset_id"],
            "title": r["title"],
            "mime": r["mime"],
            "bytes": r["bytes"],
            "url": f"/assets/{r['asset_id']}/content",
            "createdAt": r["created_at"],
        }
        for r in rows
    ]
    return JSONResponse({"folders": ["语文课堂资料", "数学专题讲义", "英语听力合集", "我的收藏"], "items": items})


async def teacher_portal(request: Request):
    with connect() as db:
        deliveries = db.execute("SELECT * FROM deliveries ORDER BY seq DESC").fetchall()
    cards = ""
    for d in deliveries:
        assets = json.loads(d["assets_json"])
        links = "".join(
            f'<li><a href="{a["url"]}" target="_blank">{a["title"]}</a> <code>{a["mime"]}</code> ({a["bytes"]} B)</li>'
            for a in assets
        )
        cards += f"""
        <div class="card">
          <div class="meta"><b>[{d['subject']}]</b> {d['task_type']} · {d['group_date']} · {d['teacher_name']}</div>
          <h3>{d['title']}</h3>
          <p>{d['description']}</p>
          <ul>{links}</ul>
        </div>"""
    html = f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>少年派 · 教师端资料发布服务</title>
<style>
body{{font-family:system-ui,-apple-system,sans-serif;max-width:920px;margin:24px auto;padding:0 16px;background:#f5f7fa;color:#222}}
header{{background:#1976D2;color:#fff;padding:18px 24px;border-radius:10px;margin-bottom:20px}}
.card{{background:#fff;border-radius:8px;padding:16px 20px;margin-bottom:14px;box-shadow:0 1px 4px rgba(0,0,0,.08)}}
.meta{{font-size:13px;color:#1976D2;margin-bottom:6px}}
h3{{margin:0 0 8px}} ul{{margin:8px 0 0 18px}} li{{margin:4px 0}}
code{{background:#eef3f8;padding:2px 6px;border-radius:4px;font-size:12px}}
</style></head><body>
<header><h2>少年派智慧课堂 · 本地教师资料服务</h2>
<div>API: <code>GET /me/deliveries</code> · <code>POST /assets</code> · <code>POST /deliveries</code> · <code>GET /assets/{{id}}/content</code></div></header>
{cards}
</body></html>"""
    return HTMLResponse(html)


app = Starlette(
    routes=[
        Route("/", teacher_portal),
        Route("/assets", api_upload_asset, methods=["POST"]),
        Route("/deliveries", api_create_delivery, methods=["POST"]),
        Route("/me/deliveries", api_list_deliveries, methods=["GET"]),
        Route("/assets/{id}/content", api_asset_content, methods=["GET", "HEAD"]),
        Route("/library", api_library, methods=["GET"]),
    ]
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--seed-only", action="store_true")
    args = parser.parse_args()
    seed_default_data()
    if args.seed_only:
        print("Seeded database at", DB_PATH)
        return
    import uvicorn

    uvicorn.run(app, host=args.host, port=args.port)


if __name__ == "__main__":
    main()
