package ua.edu.sumdu.web.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import ua.edu.sumdu.core.domain.*;

public final class Html {
  private Html() {}

  private static String e(String value) {
    return value == null
        ? ""
        : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }

  private static String u(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String shell(String title, String body, String context) {
    return "<!doctype html><html lang='uk'><meta charset='UTF-8'><meta name='viewport'"
               + " content='width=device-width,initial-scale=1'><title>"
        + e(title)
        + " | Каталог книг</title><style>"
        + CSS
        + "</style><header><a href='"
        + context
        + "/books'>КНИГА ВІДГУКІВ</a><span>Каталог технічної літератури</span></header><main>"
        + body
        + "</main><footer>Навчальний каталог · Сучасні фреймворки програмування · Лабораторна"
        + " робота 2</footer></html>";
  }

  public static String catalog(Page<Book> page, String query, String ctx) {
    StringBuilder b =
        new StringBuilder(
            "<p class='eyebrow'>Бібліотека</p><h1>Каталог книг</h1><p class='intro'>Знайдіть книгу"
                + " та поділіться враженнями про прочитане.</p><form class='search'"
                + " method='get'><label>Назва або автор<input name='q' placeholder='Пошук у"
                + " каталозі' value='"
                + e(query)
                + "'></label><label>Сортування<select name='sort'>");
    for (String option :
        new String[] {"id,asc", "title,asc", "title,desc", "author,asc", "pubYear,desc"}) {
      String label =
          switch (option) {
            case "id,asc" -> "За порядком";
            case "title,asc" -> "За назвою ↑";
            case "title,desc" -> "За назвою ↓";
            case "author,asc" -> "За автором";
            default -> "Спочатку нові видання";
          };
      b.append("<option value='")
          .append(option)
          .append("'")
          .append(page.request().sort().equals(option) ? " selected" : "")
          .append(">")
          .append(label)
          .append("</option>");
    }
    b.append(
            "</select></label><label>На сторінці<input type='number' name='size' min='1' max='100'"
                + " value='")
        .append(page.request().size())
        .append(
            "'></label><button>Знайти</button></form><div"
                + " class='sectionline'><h2>Книги</h2><span>Знайдено: ")
        .append(page.total())
        .append("</span></div><div class='books'>");
    if (page.items().isEmpty())
      b.append("<p>Книг не знайдено. Спробуйте іншу назву або автора.</p>");
    for (Book book : page.items())
      b.append("<a class='book' href='")
          .append(ctx)
          .append("/books/")
          .append(book.id())
          .append("'><span class='number'>")
          .append(String.format("%02d", book.id()))
          .append("</span><div><h3>")
          .append(e(book.title()))
          .append("</h3><p>")
          .append(e(book.author()))
          .append("</p></div><span class='year'>")
          .append(book.pubYear())
          .append("</span><span class='arrow' aria-hidden='true'>↗</span></a>");
    b.append("</div><nav class='pager' aria-label='Сторінки каталогу'>");
    for (int delta : new int[] {-1, 1}) {
      int target = page.request().page() + delta;
      boolean enabled = target >= 0 && target < page.totalPages();
      String label = delta < 0 ? "← Попередня" : "Наступна →";
      if (enabled)
        b.append("<a href='?q=")
            .append(u(query))
            .append("&amp;page=")
            .append(target)
            .append("&amp;size=")
            .append(page.request().size())
            .append("&amp;sort=")
            .append(u(page.request().sort()))
            .append("'>")
            .append(label)
            .append("</a>");
      else b.append("<span aria-disabled='true'>").append(label).append("</span>");
      if (delta < 0)
        b.append("<span>Сторінка ")
            .append(page.request().page() + 1)
            .append(" · Усього сторінок: ")
            .append(page.totalPages())
            .append("</span>");
    }
    b.append("</nav>");
    return shell("Каталог книг", b.toString(), ctx);
  }

  public static String book(Book book, Page<Comment> page, String ctx) {
    StringBuilder b =
        new StringBuilder(
            "<a class='back' href='"
                + ctx
                + "/books'>← До каталогу</a><p class='eyebrow'>Книга № "
                + book.id()
                + "</p><h1>"
                + e(book.title())
                + "</h1><p class='intro'>"
                + e(book.author())
                + " · "
                + book.pubYear()
                + "</p><div class='detail'><section><h2>Відгуки <span id='count'>"
                + page.total()
                + "</span></h2><form id='filter' class='filter'><label>Автор<input name='author'"
                + " placeholder='Усі автори'></label><label>Починаючи з дати UTC<input name='since'"
                + " type='date'></label><button class='secondary'>Застосувати</button></form><div"
                + " id='comments'>");
    if (page.items().isEmpty()) b.append("<p>Відгуків поки немає. Додайте перший.</p>");
    for (Comment c : page.items())
      b.append("<article><div class='commenthead'><strong>")
          .append(e(c.author()))
          .append("</strong><time>")
          .append(
              DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'")
                  .withZone(ZoneOffset.UTC)
                  .format(c.createdAt()))
          .append("</time></div><p class='commenttext'>")
          .append(e(c.text()))
          .append("</p><button class='delete' data-id='")
          .append(c.id())
          .append("'>Видалити</button></article>");
    b.append(
            "</div><nav class='pager'><button id='prev' class='secondary' disabled>←"
                + " Назад</button><span id='page'>Сторінка 1</span><button id='next'"
                + " class='secondary' ")
        .append(page.totalPages() <= 1 ? "disabled" : "")
        .append(
            ">Далі →</button></nav></section><aside><h2>Додати відгук</h2><form"
                + " id='commentForm'><label>Автор<input name='author' maxlength='64' required"
                + " placeholder='Ім’я'></label><label>Ваш відгук<textarea name='text'"
                + " maxlength='1000' rows='5' required placeholder='Враження про"
                + " книгу'></textarea></label><button>Додати відгук</button></form><p"
                + " class='note'>Відгук можна видалити протягом 24 годин після створення.</p><p"
                + " id='message' role='status' aria-live='polite'></p></aside></div><script>const"
                + " endpoint='")
        .append(ctx)
        .append("/books/")
        .append(book.id())
        .append("/comments';")
        .append(JS)
        .append("</script>");
    return shell(book.title(), b.toString(), ctx);
  }

  private static final String CSS =
      """
      :root{color-scheme:light;--ink:#26352c;--muted:#626c64;--line:#d8ddd6;--green:#27543c;--paper:#fafbf7}
      *{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font:17px/1.5 'Segoe UI',Arial,sans-serif}a{color:var(--green)}header{padding:22px max(6vw,24px);border-bottom:1px solid var(--line);display:flex;justify-content:space-between;gap:16px;background:white}header a{font-weight:700;letter-spacing:.12em;text-decoration:none}header span,footer{font-size:14px;color:var(--muted)}main{max-width:1160px;margin:42px auto 60px;padding:0 30px}.eyebrow{font-size:14px;text-transform:uppercase;letter-spacing:.1em;color:var(--green);margin:30px 0 8px}h1{font:600 44px/1.15 Georgia,serif;letter-spacing:-.02em;margin:0 0 15px}h2{font-size:23px;margin:0 0 20px}h3{font-size:21px;font-weight:600;margin:0 0 3px}.intro{color:var(--muted);margin:0 0 30px}.search{display:grid;grid-template-columns:2fr 1.35fr .65fr auto;align-items:end;gap:16px;padding:24px;background:#edf1e9;border-radius:5px;margin:0 0 32px}label{display:block;font-size:14px;font-weight:600}input,select,textarea{width:100%;font:16px/1.4 'Segoe UI',Arial,sans-serif;color:var(--ink);padding:11px 12px;border:1px solid #b5c0b3;border-radius:3px;background:white;margin:6px 0 0}textarea{resize:vertical}button{border:1px solid var(--green);border-radius:3px;background:var(--green);color:white;cursor:pointer;font:600 15px/1.4 'Segoe UI',Arial;padding:12px 20px;min-height:46px}button:hover{background:#173e29}a:focus-visible,button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:3px solid #b88e20;outline-offset:3px}.sectionline{display:flex;justify-content:space-between;align-items:center}.sectionline h2{margin:0}.sectionline span,.year,.number{color:var(--muted)}.books{margin-top:12px}.book{display:flex;gap:24px;align-items:center;padding:23px 12px;border-bottom:1px solid var(--line);text-decoration:none}.book:hover{background:#edf1e9}.book div{flex:1}.book p{margin:0;color:var(--muted)}.number{font-size:14px;width:24px}.year{font-variant-numeric:tabular-nums}.arrow{font-size:26px}.pager{display:flex;justify-content:space-between;align-items:center;gap:20px;margin:28px 0;font-size:14px}.pager>span[aria-disabled],button:disabled{opacity:.45;cursor:default}.back{font-size:15px;text-decoration:none}.detail{display:grid;grid-template-columns:1.55fr 1fr;gap:54px;border-top:1px solid var(--line);padding-top:30px}.filter{display:flex;gap:12px;align-items:end;margin:0 0 20px}.filter label{flex:1;min-width:0}.filter button{padding:11px}.secondary{background:transparent;color:var(--green)}.secondary:hover{background:#e4ebdf}article{padding:18px 0;border-bottom:1px solid var(--line)}.commenthead{display:flex;gap:16px;justify-content:space-between;align-items:baseline}time{font-size:13px;color:var(--muted)}.commenttext{white-space:pre-wrap;overflow-wrap:anywhere;margin:12px 0}.delete{background:none;color:#8b3f30;border:0;padding:0;min-height:32px;font-size:14px}.delete:hover{background:none;text-decoration:underline}aside{background:#edf1e9;padding:24px;align-self:start;border-radius:5px}aside label{margin-bottom:16px}aside button{width:100%}.note{font-size:14px;color:var(--muted);margin-top:20px}#message{font-size:15px;color:#8b3f30}h2 #count{font-size:16px;color:var(--muted);font-weight:400;margin-left:6px}footer{padding:22px 30px;text-align:center;border-top:1px solid var(--line)}
      @media(max-width:760px){main{padding:0 20px;margin-top:28px}header span{display:none}h1{font-size:34px}.search{grid-template-columns:1fr 1fr}.search label:first-child{grid-column:1/-1}.detail{grid-template-columns:1fr;gap:24px}.filter{flex-wrap:wrap}.commenthead{flex-wrap:wrap;gap:4px}.pager{gap:10px}.book{gap:12px}.book h3{font-size:19px}.year{font-size:14px}}
      """;
  private static final String JS =
      """
      let page=0;const msg=document.querySelector('#message');
      async function request(url,options){const r=await fetch(url,options);if(!r.ok){const data=await r.json();throw Error(data.error.message)}return r.status===204?null:r.json()}
      async function load(){const f=new FormData(document.querySelector('#filter'));const params=new URLSearchParams({page,size:10,sort:'createdAt,desc'});if(f.get('author'))params.set('author',f.get('author'));if(f.get('since'))params.set('since',f.get('since')+'T00:00:00Z');const data=await request(endpoint+'?'+params);const box=document.querySelector('#comments');box.replaceChildren();if(!data.items.length){const p=document.createElement('p');p.textContent='Відгуків не знайдено.';box.append(p)}for(const c of data.items){const a=document.createElement('article'),head=document.createElement('div'),name=document.createElement('strong'),time=document.createElement('time'),text=document.createElement('p'),button=document.createElement('button');head.className='commenthead';name.textContent=c.author;time.textContent=c.createdAt.replace('T',' ').slice(0,16)+' UTC';text.className='commenttext';text.textContent=c.text;button.className='delete';button.dataset.id=c.id;button.textContent='Видалити';head.append(name,time);a.append(head,text,button);box.append(a)}document.querySelector('#count').textContent=data.total;document.querySelector('#page').textContent='Сторінка '+(page+1);document.querySelector('#prev').disabled=page===0;document.querySelector('#next').disabled=(page+1)*10>=data.total}
      document.querySelector('#commentForm').addEventListener('submit',async e=>{e.preventDefault();const button=e.target.querySelector('button');button.disabled=true;try{await request(endpoint,{method:'POST',body:new URLSearchParams(new FormData(e.target))});e.target.reset();page=0;await load();msg.textContent='Відгук додано.'}catch(err){msg.textContent=err.message}finally{button.disabled=false}});
      document.querySelector('#comments').addEventListener('click',async e=>{const button=e.target.closest('button[data-id]');if(!button)return;button.disabled=true;try{await request(endpoint+'/'+button.dataset.id,{method:'DELETE'});await load();msg.textContent='Відгук видалено.'}catch(err){msg.textContent=err.message;button.disabled=false}});
      document.querySelector('#filter').addEventListener('submit',async e=>{e.preventDefault();page=0;try{await load()}catch(err){msg.textContent=err.message}});
      for(const [id,delta]of [['prev',-1],['next',1]])document.querySelector('#'+id).addEventListener('click',async()=>{page+=delta;try{await load()}catch(err){page-=delta;msg.textContent=err.message}});
      """;
}
