// Headless smoke test of the exact packaged assets. Uses only our isolated localhost preview.
import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const targets=await (await fetch((process.env.CDP_URL||'http://127.0.0.1:9222')+'/json/list')).json();
const target=targets.find(t=>t.type==='page'&&t.url.startsWith('http://127.0.0.1:8080'));
assert(target,'isolated preview page missing');
const ws=new WebSocket(target.webSocketDebuggerUrl);let seq=0;const pending=new Map();
await new Promise((ok,fail)=>{ws.onopen=ok;ws.onerror=fail;});
ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id&&pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}};
function call(method,params={}){return new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const r=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value;}
async function key(name){await call('Input.dispatchKeyEvent',{type:'keyDown',key:name,code:name});await call('Input.dispatchKeyEvent',{type:'keyUp',key:name,code:name});}
try {
 await call('Emulation.setDeviceMetricsOverride',{width:1920,height:1080,deviceScaleFactor:1,mobile:false});
 await call('Page.navigate',{url:'http://127.0.0.1:8080/'});
 // Readiness is driven by DOM and font state, bounded independently from the UI's clock timer.
 let ready=false;for(let i=0;i<40;i++){ready=await evaluate("document.readyState==='complete'&&typeof HomeUI!=='undefined'");if(ready)break;await new Promise(r=>setTimeout(r,100));}assert(ready,'UI did not load');
 await evaluate('document.fonts.ready.then(()=>true)');
 const baseApps=[{id:'com.xiaodianshi.tv.yst',title:'Bilibili',art:'bili',icon:''},{id:'com.netease.cloudmusic.tv',title:'网易云音乐',art:'music',icon:''}];
 await evaluate('HomeUI.update({apps:'+JSON.stringify(baseApps)+'})');
 assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.app .label')).map(x=>x.textContent)"),['Bilibili','网易云音乐']);
 assert.equal(await evaluate("document.querySelectorAll('.input').length"),4);
 assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.input .name')).map(x=>x.textContent)"),['HDMI 1','HDMI 2','HDMI 3','HDMI 4']);
 assert.equal(await evaluate("document.querySelectorAll('.dot.unknown').length"),4,'unknown ports must not show fabricated live signal');
 assert.equal(await evaluate("document.activeElement.dataset.action"),'app:com.xiaodianshi.tv.yst');
 await evaluate("window.calls=[];window.S95D={launchApp:x=>calls.push('app:'+x),openHdmi:x=>calls.push('hdmi:'+x),openSystemSettings:()=>calls.push('settings'),launcherSettings:()=>calls.push('launcherSettings'),manageApps:()=>calls.push('manage')}");
 await key('ArrowRight');await key('Enter');
 assert.equal(await evaluate('calls.pop()'),'app:com.netease.cloudmusic.tv');
 await key('ArrowRight');
 assert.equal(await evaluate('document.activeElement.dataset.action'),'app:com.netease.cloudmusic.tv','removed Mango tile remains in navigation');
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.app .tile.bili')).backgroundColor"),'rgb(237, 94, 133)');
 assert.equal(await evaluate("document.querySelector('.bili-brand').naturalWidth"),512);
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.bili-brand')).filter"),'none');
 await key('ArrowDown');await key('Enter');
 assert.equal(await evaluate('calls.pop()'),'hdmi:2');
 await key('ArrowRight');await key('ArrowRight');await key('ArrowRight');
 assert.equal(await evaluate('document.activeElement.dataset.action'),'hdmi:4','right boundary moved outside source row');
 await key('ArrowUp');await key('ArrowUp');await key('Enter');
 assert.equal(await evaluate('calls.pop()'),'settings');
 await key('ArrowDown');await key('Enter');
 assert.equal(await evaluate('calls.pop()'),'app:com.xiaodianshi.tv.yst');
 await evaluate("HomeUI.update({inputs:['connected','standby','disconnected','unexpected'],homeEnabled:true,homeRunning:false})");
 assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.dot')).map(x=>x.className)"),['dot connected','dot standby','dot disconnected','dot unknown']);
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.dot.connected')).backgroundColor"),'rgb(237, 188, 115)','connection alone must not be a green signal lamp');
 assert.equal(await evaluate("document.getElementById('home-state').textContent"),'Home 返回启动中');
 await evaluate("HomeUI.update({inputs:['detected','signal','standby','unknown']})");
 assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.status')).map(x=>x.textContent)"),['状态待确认','活跃','待机','未连接']);
 assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.dot')).map(x=>x.className)"),['dot detected','dot signal','dot standby','dot unknown']);
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.dot.signal')).backgroundColor"),'rgb(103, 223, 162)');
 // Installed-app updates previously replaced the landscape Bilibili artwork with a square icon.
 await evaluate('HomeUI.update({apps:'+JSON.stringify(baseApps.map(a=>({...a,icon:'data:image/png;base64,AA=='})))+',homeEnabled:true,homeRunning:true})');
 assert.equal(await evaluate("document.querySelector('.bili').classList.contains('has-icon')"),false);
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.bili .installed-icon')).display"),'none');
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.bili-art')).display"),'flex');
 assert(await evaluate("(()=>{let a=document.querySelector('.bili-art').getBoundingClientRect(),b=document.querySelector('.bili').getBoundingClientRect();return a.left>=b.left&&a.right<=b.right&&a.top>=b.top&&a.bottom<=b.bottom})()"),'landscape Bilibili artwork clipped');
 assert(await evaluate("Array.from(document.querySelectorAll('.input .tile')).every(el=>{let r=el.getBoundingClientRect();return r.left>=0&&r.right<=1920&&r.bottom<=1080})"),'tile clipped');
 // Reorder, hidden apps, live updates and a long remote-navigated shelf.
 await evaluate('HomeUI.update({apps:'+JSON.stringify([...baseApps].reverse())+'})');
 assert.equal(await evaluate("document.activeElement.dataset.action"),'app:com.xiaodianshi.tv.yst','sorting lost focused app identity');
 assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.app .label')).map(x=>x.textContent)"),['网易云音乐','Bilibili']);
 await evaluate("HomeUI.update({inputs:['signal','signal','unknown','unknown']})");
 assert.equal(await evaluate("document.activeElement.dataset.action"),'app:com.xiaodianshi.tv.yst','HDMI refresh stole app focus');
 const many=[...baseApps,...Array.from({length:9},(_,i)=>({id:'fixture.app'+i,title:i===0?'<img src=x onerror=alert(1)>':'应用 '+i,art:'generic',icon:''}))];
 await evaluate('HomeUI.update({apps:'+JSON.stringify(many)+'})');
 assert.equal(await evaluate("document.querySelectorAll('.app .label img').length"),0,'app label injected markup');
 for(let i=0;i<15;i++)await key('ArrowRight');
 await new Promise(r=>setTimeout(r,500));
 assert.equal(await evaluate('document.activeElement.dataset.action'),'app:fixture.app8','long shelf boundary');
 assert(await evaluate("(()=>{let r=document.activeElement.querySelector('.tile').getBoundingClientRect();return r.left>=90&&r.right<=1830&&document.getElementById('apps-row').scrollLeft>0})()"),'last app not revealed by remote scrolling');
 await evaluate('HomeUI.update({apps:'+JSON.stringify(baseApps)+'})');
 assert.equal(await evaluate('document.activeElement.dataset.action'),'app:com.netease.cloudmusic.tv','removed focused app not clamped');
 await evaluate('HomeUI.update({apps:[]})');
 assert.equal(await evaluate('document.activeElement.dataset.action'),'manage','empty shelf has no focusable add action');
 await key('Enter');assert.equal(await evaluate('calls.pop()'),'manage');
 await key('ArrowDown');assert.equal(await evaluate('document.activeElement.dataset.action'),'hdmi:1');
 await key('ArrowUp');assert.equal(await evaluate('document.activeElement.dataset.action'),'manage');
 // Restore the unmodified visual preview with unknown input states before capture.
 await call('Page.navigate',{url:'http://127.0.0.1:8080/'});
 for(let i=0;i<40;i++){if(await evaluate("document.readyState==='complete'&&typeof HomeUI!=='undefined'"))break;await new Promise(r=>setTimeout(r,100));}
 await evaluate('document.fonts.ready.then(()=>true)');
 await evaluate("Promise.all(Array.from(document.images).map(x=>x.decode().catch(()=>{})))");
 await evaluate('HomeUI.update({apps:'+JSON.stringify([...baseApps,{id:'io.github.jqssun.airplay',title:'AirPlay Server',art:'airplay',icon:''}])+'})');
 await evaluate("Promise.all(Array.from(document.images).map(x=>x.decode().catch(()=>{})))");
 assert(await evaluate("document.querySelector('.airplay-symbol').naturalWidth>0"),'AirPlay symbol missing');
 assert.equal(await evaluate("document.querySelector('.airplay').classList.contains('has-icon')"),false);
 await evaluate('HomeUI.update({apps:'+JSON.stringify([...baseApps,{id:'org.s95dhome.airplay',title:'TV Airplay',art:'airplay',icon:'data:image/png;base64,AA=='}])+'})');
 await evaluate("Promise.all(Array.from(document.images).map(x=>x.decode().catch(()=>{})))");
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.airplay .installed-icon')).display"),'none','installed icon replaced custom AirPlay logo');
 assert.equal(await evaluate("getComputedStyle(document.querySelector('.airplay')).backgroundImage"),'linear-gradient(115deg, rgb(229, 232, 234), rgb(174, 182, 187))');
 await evaluate("new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))");
 const screenshot=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});
 const path=resolve(process.argv[2]||'artifacts/home-preview.png');await writeFile(path,Buffer.from(screenshot.data,'base64'));
 console.log('PASS: dynamic apps, reorder/hide/empty shelf, focus persistence, long-row scrolling, escaped labels, package actions; artwork; four HDMI ports and statuses; 1920x1080 preview (installed-app fixture).');
 console.log(path);
}finally{ws.close();}
