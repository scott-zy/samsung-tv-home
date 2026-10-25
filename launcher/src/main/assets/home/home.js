/* Local UI only; Native actions are restricted to this packaged page. No fetch or network assets. */
(function(){'use strict';
 var stage=document.getElementById('stage'), gear=document.getElementById('settings');
 var apps=Array.prototype.slice.call(document.querySelectorAll('.app'));
 var inputs=Array.prototype.slice.call(document.querySelectorAll('.input'));
 var rows=[[gear],apps,inputs], row=1, col=0, timer, dayKey='';
 var appSignature='';
 var musicTemplate=document.querySelector('.music').innerHTML;
 function fit(){var scale=Math.min(innerWidth/1920,innerHeight/1080);stage.style.transform='scale('+scale+')';stage.style.left=(innerWidth-1920*scale)/2+'px';stage.style.top=(innerHeight-1080*scale)/2+'px';}
 function focus(r,c){row=Math.max(0,Math.min(2,r));col=Math.max(0,Math.min(rows[row].length-1,c));var el=rows[row][col];el.focus();if(row===1)reveal(el);}
 function reveal(el){var track=document.getElementById('apps-row'),left=el.offsetLeft-12,right=el.offsetLeft+el.offsetWidth+12;if(left<track.scrollLeft)track.scrollLeft=left;else if(right>track.scrollLeft+track.clientWidth)track.scrollLeft=right-track.clientWidth;}
 function toast(text){var t=document.getElementById('toast');t.textContent=text;t.classList.add('visible');clearTimeout(timer);timer=setTimeout(function(){t.classList.remove('visible');},4200);}
 function act(action){
  if(typeof S95D==='undefined'){toast('界面预览 · '+(action==='settings'?'首页设置':action));return;}
  if(action==='settings')S95D.openSystemSettings();
  else if(action==='manage')S95D.manageApps();
  else if(action.indexOf('app:')===0)S95D.launchApp(action.slice(4));
  else if(action.indexOf('hdmi:')===0)S95D.openHdmi(Number(action.slice(5)));
 }
 function bind(el,r,c){el.addEventListener('focus',function(){row=r;col=c;if(r===1)reveal(el);});el.addEventListener('click',function(){focus(r,c);act(el.getAttribute('data-action'));});}
 rows.forEach(function(items,r){items.forEach(function(el,c){bind(el,r,c);});});
 function renderApps(data){
  var signature=JSON.stringify(data);if(signature===appSignature)return;appSignature=signature;
  var active=document.activeElement,oldAction=active&&active.getAttribute('data-action'),hadApp=apps.indexOf(active)>=0,oldCol=col;
  var container=document.getElementById('apps-row');container.textContent='';
  data.forEach(function(app){
   if(!app||typeof app.id!=='string'||!app.id.match(/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/))return;
   var button=document.createElement('button');button.className='app focusable';button.setAttribute('data-action','app:'+app.id);button.setAttribute('aria-label','打开 '+String(app.title||app.id));
   var tile=document.createElement('span');tile.className='tile';
   if(app.art==='bili'){
    tile.className+=' bili custom-artwork';var art=document.createElement('span');art.className='bili-art';var brand=document.createElement('img');brand.className='bili-brand';brand.src='bilibili.png';brand.alt='';art.appendChild(brand);tile.appendChild(art);
   }else if(app.art==='music'){tile.className+=' music';tile.innerHTML=musicTemplate;}
   else if(app.art==='airplay'){tile.className+=' airplay custom-artwork';tile.innerHTML='<img class="airplay-symbol" src="airplay-mark.svg" alt="">';}
   else{tile.className+=' generic';var fallback=document.createElement('span');fallback.className='fallback generic-mark';fallback.textContent=String(app.title||'?').slice(0,1);tile.appendChild(fallback);}
   var image=tile.querySelector('.installed-icon');if(!image){image=document.createElement('img');image.className='installed-icon';image.alt='';tile.appendChild(image);}
   if(app.art!=='bili'&&app.art!=='airplay'&&typeof app.icon==='string'&&app.icon.indexOf('data:image/png;base64,')===0){image.src=app.icon;tile.classList.add('has-icon');}
   var label=document.createElement('span');label.className='label';label.textContent=String(app.title||app.id);button.appendChild(tile);button.appendChild(label);container.appendChild(button);
  });
  if(!container.children.length){var empty=document.createElement('button');empty.className='app focusable';empty.setAttribute('data-action','manage');empty.setAttribute('aria-label','添加应用');empty.innerHTML='<span class="tile generic"><span class="manage-mark">＋</span></span><span class="label">添加应用</span>';container.appendChild(empty);}
  apps=Array.prototype.slice.call(container.querySelectorAll('.app'));rows[1]=apps;apps.forEach(function(el,c){bind(el,1,c);});
  if(hadApp){var match=apps.findIndex(function(el){return el.getAttribute('data-action')===oldAction;});focus(1,match>=0?match:Math.min(oldCol,apps.length-1));}
 }
 function key(name){
  if(name==='ArrowLeft')focus(row,col-1);
  else if(name==='ArrowRight')focus(row,col+1);
  else if(name==='ArrowUp')focus(row-1,row===1?0:col);
  else if(name==='ArrowDown')focus(row+1,col);
  else if(name==='Enter'){act(rows[row][col].getAttribute('data-action'));}
  else if(name==='Menu'){if(typeof S95D!=='undefined')S95D.launcherSettings();else toast('首页设置 · 壁纸与 Home 返回');}
  else if(name==='Back'){if(document.getElementById('toast').classList.contains('visible'))document.getElementById('toast').classList.remove('visible');}
 }
 document.addEventListener('keydown',function(e){var name=e.key;if(name===' '||name==='Select')name='Enter';if(name==='ContextMenu'||e.keyCode===82)name='Menu';if(name==='Escape'||name==='Backspace')name='Back';if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Enter','Menu','Back'].indexOf(name)>=0){e.preventDefault();if(!e.repeat||name.indexOf('Arrow')===0)key(name);}});
 function clock(){var d=new Date(),w=['星期日','星期一','星期二','星期三','星期四','星期五','星期六'];document.getElementById('time').textContent=('0'+d.getHours()).slice(-2)+':'+('0'+d.getMinutes()).slice(-2);document.getElementById('date').textContent=d.getFullYear()+'年'+(d.getMonth()+1)+'月'+d.getDate()+'日  '+w[d.getDay()];var k=d.getFullYear()+'-'+d.getMonth()+'-'+d.getDate();if(k===dayKey)return;dayKey=k;document.getElementById('month').textContent=(d.getMonth()+1)+'月  '+d.getFullYear();var grid=document.getElementById('calendar');grid.textContent='';['一','二','三','四','五','六','日'].forEach(function(x){var el=document.createElement('span');el.className='weekday';el.textContent=x;grid.appendChild(el);});var first=(new Date(d.getFullYear(),d.getMonth(),1).getDay()+6)%7,days=new Date(d.getFullYear(),d.getMonth()+1,0).getDate();for(var i=0;i<first+days;i++){var el=document.createElement('span'),n=i-first+1;el.className='day'+(n===d.getDate()?' today':'');el.textContent=i<first?'':n;grid.appendChild(el);}}
 function update(data){
  if(!data)return;
  (data.inputTitles||[]).forEach(function(title,i){if(inputs[i]){inputs[i].querySelector('.name').textContent=String(title);inputs[i].setAttribute('aria-label','切换至 '+String(title)+' HDMI '+(i+1));}});
  var labels={unknown:'未连接',detected:'状态待确认',connected:'待机',standby:'待机',disconnected:'未连接',signal:'活跃'};
  (data.inputs||[]).forEach(function(state,i){if(!inputs[i])return;var s=Object.prototype.hasOwnProperty.call(labels,state)?state:'unknown';inputs[i].querySelector('.dot').className='dot '+s;inputs[i].querySelector('.status').textContent=labels[s];});
  if(Array.isArray(data.apps))renderApps(data.apps);
  if(typeof data.homeEnabled==='boolean')document.getElementById('home-state').textContent=data.homeEnabled?(data.homeRunning?'Home 返回已开启':'Home 返回启动中'):'Home 返回已关闭';
 }
 window.HomeUI={update:update,key:key,toast:toast,refresh:clock,wallpaper:function(value){if(value==='night-sky.jpg'||value==='milky-way.jpg'||value.indexOf('data:image/jpeg;base64,')===0)document.getElementById('wallpaper').style.backgroundImage='url("'+value+'")';}};
 if(typeof S95D==='undefined')document.getElementById('home-state').textContent='界面预览';
 addEventListener('resize',fit);fit();clock();setInterval(clock,10000);focus(1,0);
})();
