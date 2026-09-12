#!/usr/bin/env python3
import json, os, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

HOST='0.0.0.0'; PORT=18080
BASE='http://10.0.2.2:18080'

def cats(prefix, names):
    return [{"category_id": str(i+1), "category_name": n} for i,n in enumerate(names)]

LIVE_CATS=cats('l',["السعودية","رياضة","أخبار","أطفال","وثائقيات","ترفيه"])
VOD_CATS=cats('v',["أحدث الأفلام","أكشن","دراما","كوميديا","عائلي","وثائقي"])
SERIES_CATS=cats('s',["أحدث المسلسلات","خليجي","عربي","أجنبي","جريمة","عائلي"])

LIVE=[]
for c in LIVE_CATS:
    cid=c['category_id']
    for j in range(1,9):
        i=int(cid)*100+j
        LIVE.append({
            "stream_id":i,"name":f"قناة {c['category_name']} {j} HD","category_id":cid,
            "stream_icon":f"{BASE}/poster/{i}.png","epg_channel_id":f"demo.{i}",
            "tv_archive":1 if j%3==0 else 0,"tv_archive_duration":7 if j%3==0 else 0
        })

VOD=[]
for c in VOD_CATS:
    cid=c['category_id']
    for j in range(1,9):
        i=1000+int(cid)*100+j
        VOD.append({
            "stream_id":i,"name":f"فيلم تجريبي {i}","category_id":cid,
            "stream_icon":f"{BASE}/poster/{i}.png","rating":str(round(6.7+(j%4)*0.6,1)),
            "year":str(2019+(j%7)),"genre":"أكشن، دراما" if j%2 else "كوميديا، عائلي",
            "plot":"محتوى تجريبي لاختبار الواجهة وحركة المؤشر وسرعة التنقل داخل BRO PLUS TV.",
            "duration":"01:42:00","container_extension":"mp4","added":str(1700000000+i)
        })

SERIES=[]
for c in SERIES_CATS:
    cid=c['category_id']
    for j in range(1,7):
        i=2000+int(cid)*100+j
        SERIES.append({
            "series_id":i,"name":f"مسلسل تجريبي {i}","category_id":cid,
            "cover":f"{BASE}/poster/{i}.png","backdrop_path":[f"{BASE}/poster/{i}.png"],
            "rating":str(round(7.1+(j%3)*0.7,1)),"releaseDate":str(2020+(j%6)),
            "genre":"دراما، تشويق","plot":"مسلسل تجريبي لاختبار المواسم والحلقات والأزرار والترجمة والمشغل.",
            "last_modified":str(1710000000+i)
        })

# Small valid 32x48 PNG, repeated for all demo art.
PNG=bytes.fromhex('89504e470d0a1a0a0000000d49484452000000200000003008020000000e6f86ee0000001749444154789cedc101010000008090feafee080a00000000bc0c3000019051df750000000049454e44ae426082')

def series_info(sid):
    item=next((x for x in SERIES if x['series_id']==sid), SERIES[0])
    eps={}
    eid=sid*10
    for season in (1,2,3):
        arr=[]
        for n in range(1,7):
            arr.append({"id":eid+season*100+n,"episode_num":n,"title":f"الحلقة {n}","container_extension":"mp4","info":{"duration":"00:42:00","plot":f"الحلقة {n} من الموسم {season}","movie_image":item['cover']}})
        eps[str(season)]=arr
    return {"info":{"cover":item['cover'],"backdrop_path":item['backdrop_path'],"rating":item['rating'],"releaseDate":item['releaseDate'],"genre":item['genre'],"plot":item['plot']},"episodes":eps}

class H(BaseHTTPRequestHandler):
    def log_message(self, fmt,*args):
        print('%s - %s' % (self.address_string(), fmt%args), flush=True)
    def send_json(self,obj):
        raw=json.dumps(obj,ensure_ascii=False).encode()
        self.send_response(200); self.send_header('Content-Type','application/json; charset=utf-8'); self.send_header('Content-Length',str(len(raw))); self.end_headers(); self.wfile.write(raw)
    def do_GET(self):
        u=urlparse(self.path); q=parse_qs(u.query); path=u.path
        if path=='/health': return self.send_json({'ok':True})
        if path=='/player_api.php':
            action=q.get('action',[''])[0]
            if not action:
                return self.send_json({"user_info":{"auth":1,"status":"Active","exp_date":str(int(time.time())+86400*365)},"server_info":{"url":BASE}})
            cid=q.get('category_id',[''])[0]
            if action=='get_live_categories': return self.send_json(LIVE_CATS)
            if action=='get_live_streams': return self.send_json([x for x in LIVE if not cid or x['category_id']==cid])
            if action=='get_vod_categories': return self.send_json(VOD_CATS)
            if action=='get_vod_streams': return self.send_json([x for x in VOD if not cid or x['category_id']==cid])
            if action=='get_series_categories': return self.send_json(SERIES_CATS)
            if action=='get_series': return self.send_json([x for x in SERIES if not cid or x['category_id']==cid])
            if action=='get_series_info': return self.send_json(series_info(int(q.get('series_id',['0'])[0] or 0)))
            if action in ('get_simple_data_table','get_short_epg'):
                now=int(time.time()); entries=[]
                for k in range(8):
                    st=now-1800+k*3600; en=st+3600
                    entries.append({"title":f"برنامج تجريبي {k+1}","description":"EPG تجريبي لاختبار معلومات البث.","start_timestamp":st,"stop_timestamp":en})
                return self.send_json({"epg_listings":entries})
            return self.send_json([])
        if path.startswith('/poster/'):
            self.send_response(200); self.send_header('Content-Type','image/png'); self.send_header('Content-Length',str(len(PNG))); self.end_headers(); return self.wfile.write(PNG)
        if path.startswith('/live/'):
            f='/tmp/brotv-demo.ts'; ctype='video/mp2t'
        elif path.startswith('/movie/') or path.startswith('/series/'):
            f='/tmp/brotv-demo.mp4'; ctype='video/mp4'
        else:
            self.send_response(404); self.end_headers(); return
        if not os.path.exists(f): self.send_response(503); self.end_headers(); return
        data=open(f,'rb').read()
        self.send_response(200); self.send_header('Content-Type',ctype); self.send_header('Accept-Ranges','bytes'); self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data)

if __name__=='__main__':
    print(f'BRO PLUS TV demo server on {HOST}:{PORT}', flush=True)
    ThreadingHTTPServer((HOST,PORT),H).serve_forever()
