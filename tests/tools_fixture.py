from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
import json,threading
TAGS=[dict(id=1,name='Alpha',namespace='general',count=2),dict(id=2,name='Beta',namespace='artist',count=3),dict(id=3,name='Unused',namespace='general',count=0)]
EVENTS=[]
class H(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def send(self,obj,code=200):
  data=json.dumps(obj).encode();self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
 def do_GET(self):
  p=self.path.split('?')[0]
  if p=='/auth/me':self.send(dict(id=1,username='QA',is_admin=True,is_active=True))
  elif p=='/auth/status':self.send(dict(has_users=True))
  elif p=='/tags':self.send(TAGS)
  elif p=='/mobile/media':self.send([])
  elif p=='/stats/overview':self.send(dict(total=10,favorites=2,average_rating=4.0,total_size_bytes=1048576,total_duration_seconds=3600,missing=0,by_type=dict(video=4,manga=3,image=2,audio=1),view_status=dict(unviewed=5,viewing=3,viewed=2)))
  elif p=='/stats/distribution':self.send(dict(by_source=dict(local=6,x=4),rating_histogram={'5':5,'4':3}))
  elif p=='/stats/activity':self.send(dict(total=6,max=3,buckets=[dict(date='2026-10-01',count=3)]))
  elif p=='/events':self.send(dict(tags=TAGS,events=EVENTS))
  elif p=='/download':
   data=b'he-manager-test-download';self.send_response(200);self.send_header('Content-Type','application/octet-stream');self.send_header('Content-Disposition','attachment; filename="he-qa.txt"');self.end_headers();self.wfile.write(data)
  else:
   data=b'''<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{color:white;background:#141720;font:20px sans-serif}button,input,a{display:block;margin:24px;padding:16px} .he-app-shell{height:var(--he-app-height,100dvh)}</style></head><body><main class="he-app-shell"><h1 id="status"></h1><input type="file" id="file"><button id="confirm" onclick="document.querySelector('#result').textContent=confirm('QA confirmation')?'CONFIRMED':'CANCELLED'">Confirm test</button><button id="prompt" onclick="document.querySelector('#result').textContent=prompt('QA prompt','value')">Prompt test</button><a href="/download" download="he-qa.txt">Download test</a><p id="result"></p></main><script>document.querySelector('#status').textContent=localStorage.getItem('he_manager_token')==='qa-token'?'SESSION OK':'SESSION MISSING';</script></body></html>'''
   self.send_response(200);self.send_header('Content-Type','text/html');self.end_headers();self.wfile.write(data)
 def body(self):return json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))))
 def do_PATCH(self):
  body=self.body();tag=next(t for t in TAGS if t['id']==int(self.path.split('/')[-1]));tag.update(body);EVENTS.append(['edit',tag['id'],body]);self.send(tag)
 def do_POST(self):
  body=self.body()
  if self.path.endswith('/merge'):
   source=int(self.path.split('/')[2]);target=next(t for t in TAGS if t['id']==body['target_id']);source_tag=next(t for t in TAGS if t['id']==source);target['count']+=source_tag['count'];TAGS.remove(source_tag);EVENTS.append(['merge',source,target['id']])
  self.send({})
 def do_DELETE(self):
  tid=int(self.path.split('/')[-1]);TAGS[:]=[t for t in TAGS if t['id']!=tid];EVENTS.append(['delete',tid]);self.send({})
threading.Thread(target=ThreadingHTTPServer(('127.0.0.1',18011),H).serve_forever,daemon=True).start()
ThreadingHTTPServer(('127.0.0.1',18010),H).serve_forever()
