"""Isolated API fixture for native UI QA; never contacts the real service."""
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
import json, threading, struct, zlib
from urllib.parse import urlsplit,parse_qs

def png():
 def chunk(t,b):return struct.pack('!I',len(b))+t+b+struct.pack('!I',zlib.crc32(t+b)&0xffffffff)
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',32,32,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+b'\xff\xc9\x45'*32)*32))+chunk(b'IEND',b'')
PNG=png();EVENTS=[];TAGS=[dict(id=1,name='Alpha',namespace='general',count=2),dict(id=2,name='Beta',namespace='artist',count=3),dict(id=3,name='Unused',namespace='general',count=0)]
MEDIA=[dict(id=i,title='QA media %02d'%i,media_type='manga',cover_path='qa.png',duration=10,progress=0,rating=0,favorite=False,view_status='unviewed',tags=[],is_missing=False,absolute_path='/qa/media/%d'%i,source_url='',source_site='local',file_size=100,width=32,height=32,page_count=2) for i in range(1,56)]
USERS=[dict(id=1,username='QA',is_admin=True,is_active=True,created_at='2026-10-01'),dict(id=2,username='guest',is_admin=False,is_active=True,created_at='2026-10-01')]
SOURCES=[dict(id=i,source_type=t,name=t.upper(),favorites_url='http://qa.invalid/favorites',download_root_path='/qa/downloads',status='ok',cookie_saved=True,auto_sync_enabled=False,auto_sync_interval_hours=24) for i,t in [(1,'wnacg'),(2,'asmr')]]
FAVORITES=[dict(id=i,source_id=1 if i<30 else 2,source_type='wnacg' if i<30 else 'asmr',title='QA external %02d'%i,category_name='测试分类',local_media_id=1 if i==1 else None,url='https://qa.invalid/%d'%i) for i in range(1,56)]
FOLDERS=[dict(id=1,path='/qa/library',scan_mode='manga',thumbnail_enabled=True,thumbnail_interval=1,status='idle')]
XSOURCE=dict(id=1,name='X QA',download_root_path='/qa/x',cookie_saved=True,auto_sync_enabled=False,auto_sync_interval_hours=24)
XSTATS=dict(total_posts=2,completed_posts=0,failed_posts=1,skipped_posts=0,total_media=2,downloaded_media=0)
JOBS={};JOB_POLLS={};PAIR_STATUS='pending';PROXY='';PAW_CONNECTED=True
CREATOR=dict(service='patreon',creator_id='qa',creator_name='QA creator')
PAW_POST=dict(post_key='pawchive:patreon:qa:1',service='patreon',creator_id='qa',post_id='1',title='QA post',creator_name='QA creator',published_at='2026-10-01',reported_attachment_count=2,attachments=[dict(attachment_key='a1',filename='image1.png',media_type='image',stream_ref='qa1',preview_ref='qa1',availability='playable'),dict(attachment_key='a2',filename='image2.png',media_type='image',stream_ref='qa2',preview_ref='qa2',availability='playable')])

def job(kind):
 key=kind+'-qa';JOBS[key]=dict(job_id=key,status='running',total=2,completed=0,failed=0,message='QA task',source_id=1);JOB_POLLS[key]=0
 return JOBS[key]
class H(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def send(self,data,code=200,total=None):
  raw=json.dumps(data,ensure_ascii=False).encode();self.send_response(code);self.send_header('Content-Type','application/json; charset=utf-8')
  if total is not None:self.send_header('X-Total-Count',str(total))
  self.end_headers();self.wfile.write(raw)
 def urlpath(self):return urlsplit(self.path).path
 def qs(self):return parse_qs(urlsplit(self.path).query)
 def do_GET(self):
  p=self.urlpath();q=self.qs();offset=int(q.get('offset',[0])[0]);limit=int(q.get('limit',[24])[0])
  if p=='/events':return self.send(EVENTS)
  if p=='/auth/me':return self.send(USERS[0])
  if p=='/auth/status':return self.send(dict(has_users=True))
  if p in ['/media','/mobile/media']:return self.send(MEDIA[offset:offset+limit],total=len(MEDIA))
  if p.startswith('/media/') and p.count('/')==2:return self.send(next(m for m in MEDIA if m['id']==int(p.split('/')[-1])))
  if p=='/tags':return self.send(TAGS)
  if p=='/users':return self.send(USERS)
  if p=='/folders':return self.send(FOLDERS)
  if p=='/search-folder':return self.send(dict(results=['/qa/library']))
  if p=='/external/proxy':return self.send(dict(proxy=PROXY))
  if p=='/external/sources':return self.send(SOURCES)
  if p=='/external/favorites':
   items=[f for f in FAVORITES if f['source_type']==q.get('source_type',['wnacg'])[0]]
   return self.send(items[offset:offset+limit],total=len(items))
  if p.startswith('/mobile/thumbnails/') or p.endswith('/cover') or '/pawchive/media/' in p:
   self.send_response(200);self.send_header('Content-Type','image/png');self.end_headers();self.wfile.write(PNG);return
  if p=='/stats/overview':return self.send(dict(total=55,total_size_bytes=1073741824,favorites=2,average_rating=4.5,total_duration_seconds=7200,by_type=dict(manga=55),view_status=dict(unviewed=55)))
  if p=='/stats/distribution':return self.send(dict(by_source=dict(local=55),rating_histogram={'5':2,'0':53},growth=[dict(month='2026-10',added=55,cumulative=55)]))
  if p=='/stats/activity':return self.send(dict(total=3,max=3,to_date='2026-10-01',buckets=[dict(date='2026-10-01',count=3)],by_type=dict(manga=[dict(date='2026-10-01',count=3)])))
  if p=='/stats/attention':return self.send(dict(dusty=[MEDIA[0]],unrated=[MEDIA[1]]))
  if p=='/stats/highlights':return self.send(dict(top_creators=[],top_tags=[dict(name='QA',namespace='general',count=2)],top_videos=[MEDIA[2]]))
  if p=='/auto-sync/logs':return self.send([dict(started_at='2026-10-01',status='ok',message='QA sync completed')])
  if p=='/x/sources':return self.send([XSOURCE])
  if p.endswith('/stats') and p.startswith('/x/'):return self.send(XSTATS)
  if p.endswith('/posts') and p.startswith('/x/'):return self.send([dict(id=1,tweet_id='1',full_text='QA X post',author_screen_name='qa',status='pending')])
  if p.endswith('/active-job'):return self.send(JOBS.get('x-qa'))
  if p.endswith('/active-sync'):return self.send(JOBS.get('x-sync-qa'))
  if p=='/external/pawchive/account/status':return self.send(dict(connected=PAW_CONNECTED))
  if p=='/external/pawchive/account/favorites':return self.send(dict(items=[CREATOR] if PAW_CONNECTED else []))
  if p=='/external/pawchive/posts':return self.send(dict(items=[PAW_POST],next_cursor=None,has_more=False,warnings=[]))
  if p.startswith('/external/pawchive/posts/'):return self.send(PAW_POST)
  if p=='/external/pawchive/downloads':return self.send(dict(items=[j for k,j in JOBS.items() if k.startswith('paw')]))
  if p=='/ai/recommendations/status':return self.send(dict(deepseek_configured=False,key_saved=False,env_key_present=False,model='qa-model',base_url='https://qa.invalid'))
  if p in ['/recommend/manga-profiles/stats','/recommend/manga-metadata/stats']:return self.send(dict(total_manga=55,profiled=2,missing=53,stale=0))
  if p=='/dedup/summary':return self.send(dict(pending_pairs=1 if PAIR_STATUS=='pending' else 0,queue_size=0,checking=0,strong_duplicate=1))
  if p=='/dedup/candidates-page':return self.send(dict(total=1,items=[dict(id=1,status=PAIR_STATUS,similarity=99,level='strong_duplicate',reason='QA checksum matches',existing=MEDIA[0],candidate=MEDIA[1])]))
  if p=='/bd2/spine':return self.send(dict(root='/qa/bd2',assets=[]))
  if p=='/bd2/spine/download/status':return self.send(dict(status='idle'))
  key=p.split('/')[-1]
  if key in JOBS:
   JOB_POLLS[key]+=1
   if JOB_POLLS[key]>=2:JOBS[key].update(status='completed',completed=2)
   return self.send(JOBS[key])
  return self.send(dict(detail='Fixture GET missing: '+p),404)
 def body(self):
  if 'chunked' in self.headers.get('Transfer-Encoding',''):
   data=b''
   while True:
    size=int(self.rfile.readline().strip(),16)
    if size==0:self.rfile.readline();break
    data+=self.rfile.read(size);self.rfile.read(2)
   return {'upload_bytes':len(data)}
  data=self.rfile.read(int(self.headers.get('Content-Length',0)))
  if self.headers.get('Content-Type','').startswith('multipart/'):return {'upload_bytes':len(data)}
  return json.loads(data or b'{}')
 def mutate(self):
  global PROXY,PAIR_STATUS,PAW_CONNECTED
  p=self.urlpath();b=self.body();EVENTS.append(dict(method=self.command,path=p,body=b))
  if p.startswith('/tags/'):
   id=int(p.split('/')[2]);target=next(t for t in TAGS if t['id']==id)
   if self.command=='PATCH':target.update(b);return self.send(target)
   if p.endswith('/merge'):
    into=next(t for t in TAGS if t['id']==b['target_id']);into['count']+=target['count'];TAGS.remove(target);return self.send(dict(ok=True))
   if self.command=='DELETE':TAGS.remove(target);return self.send(dict(ok=True))
  if p.startswith('/media/'):
   id=int(p.split('/')[2]);m=next(m for m in MEDIA if m['id']==id)
   if p.endswith('/tags'):m['tags'].append(dict(id=10,name=b['name']))
   elif self.command=='PATCH':m.update(b)
   return self.send(m)
  if p=='/users':
   u=dict(id=max(u['id'] for u in USERS)+1,username=b['username'],is_admin=b.get('is_admin',False),is_active=True);USERS.append(u);return self.send(u)
  if p.startswith('/users/'):
   u=next(u for u in USERS if u['id']==int(p.split('/')[-1]))
   if self.command=='DELETE':USERS.remove(u);return self.send(dict(ok=True))
   u.update({k:v for k,v in b.items() if k!='password'});return self.send(u)
  if p=='/external/proxy':PROXY=b.get('proxy','');return self.send(dict(proxy=PROXY))
  if p.startswith('/auto-sync/'):
   if p.endswith('/trigger'):return self.send(dict(ok=True))
   source=XSOURCE if '/x/' in p else SOURCES[0];source.update(b);return self.send(source)
  if p.startswith('/external/sources/'):
   s=next(s for s in SOURCES if s['id']==int(p.split('/')[-1]));s.update(b);return self.send(s)
  if p in ['/external/wnacg/sync','/external/asmr/sync']:return self.send(dict(source=SOURCES[0 if 'wnacg' in p else 1],synced_count=2))
  if p.endswith('/downloads') and '/pawchive/' not in p:return self.send(job('external'))
  if p.endswith('/downloads/preview'):return self.send(dict(selected_posts=len(b['selections']),supported=2,unsupported=0,already_downloaded=0))
  if p=='/external/pawchive/downloads':return self.send(job('paw'))
  if p=='/external/pawchive/account/login':PAW_CONNECTED=True;return self.send(dict(items=[CREATOR]))
  if p=='/external/pawchive/account/session':PAW_CONNECTED=False;self.send_response(204);self.end_headers();return
  if '/pawchive/account/favorites/' in p:self.send_response(204);self.end_headers();return
  if p.startswith('/x/sources/'):
   if p.endswith('/archive'):return self.send(dict(new_posts=2,existing_posts=0))
   if p.endswith('/sync'):return self.send(job('x-sync'))
   XSOURCE.update(b);return self.send(XSOURCE)
  if p=='/x/imports':return self.send(job('x'))
  if '/x/imports/' in p:
   j=JOBS['x-qa'];j['status']={'pause':'paused','resume':'running','cancel':'canceled'}[p.split('/')[-1]];return self.send(j)
  if p=='/recommend/manga':return self.send(dict(candidate_count=55,ai_enabled=False,recommendations=[dict(media=MEDIA[0],reason='QA preference matched',matched_tags=['QA'],score=99)]))
  if p.endswith('/analyze'):return self.send(job('profiles' if 'profiles' in p else 'metadata'))
  if p.endswith('/resolve'):PAIR_STATUS=b['action'];return self.send(dict(ok=True))
  if p=='/folders':FOLDERS.append(dict(id=2,path=b['path'],status='idle',**{k:v for k,v in b.items() if k!='path'}));return self.send(FOLDERS[-1])
  if p.startswith('/folders/') and self.command=='DELETE':FOLDERS[:]=[f for f in FOLDERS if f['id']!=int(p.split('/')[-1])]
  return self.send(dict(ok=True))
 do_POST=mutate
 do_PUT=mutate
 do_PATCH=mutate
 do_DELETE=mutate
ThreadingHTTPServer(('127.0.0.1',18010),H).serve_forever()
