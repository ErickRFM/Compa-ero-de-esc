import importlib.util,time,json,sys
from pathlib import Path
s=importlib.util.spec_from_file_location('q','infrastructure/scripts/v8-emulator-qa.py');q=importlib.util.module_from_spec(s);s.loader.exec_module(q)
for label,density,size,font in [('360',480,'1080x2400','1.0'),('390',443,'1080x2400','1.0'),('430',402,'1080x2400','1.0'),('tablet-800',320,'1600x2560','1.0'),('390-font2',443,'1080x2400','2.0')]:
 if len(sys.argv)>1 and label!=sys.argv[1]:continue
 q.adb('shell','wm','size',size);q.adb('shell','wm','density',str(density));q.adb('shell','settings','put','system','font_scale',font);time.sleep(1)
 q.tap('Inicio');q.capture('responsive/'+label+'/home');q.tap('Horario')
 for _ in range(3):q.adb('shell','input','swipe','500','500','500','1850','350')
 q.tap('Semana');q.capture('responsive/'+label+'/week')
 q.tap('Clases')
 for attempt in range(8):
  try:
   q.tap('Canal de clase');break
  except RuntimeError:
   q.adb('shell','input','swipe','500','1800','500','500','350')
 else:raise RuntimeError('Class channel action not reachable by scrolling')
 for _ in range(4):q.adb('shell','input','swipe','500','500','500','1850','250')
 q.capture('responsive/'+label+'/channel')
 q.adb('shell','input','swipe','500','1800','500','500','400');q.capture('responsive/'+label+'/channel-scroll')
 q.tap('Asistencia')
 for _ in range(4):q.adb('shell','input','swipe','500','500','500','1850','250')
 q.capture('responsive/'+label+'/attendance');q.adb('shell','input','swipe','500','1800','500','500','400');q.capture('responsive/'+label+'/attendance-scroll')
 print('Captured '+label,flush=True)
q.adb('shell','settings','put','system','font_scale','1.0');q.adb('shell','wm','size','1080x2400');q.adb('shell','wm','density','443')
