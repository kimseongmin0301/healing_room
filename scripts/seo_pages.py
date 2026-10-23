"""
검색엔진(SEO)·AI 답변(AEO/GEO)용 정적 페이지를 만든다.

  python scripts/seo_pages.py

만드는 것 (healing-room/src/main/resources/ 아래. 주소는 .html 없이 SiteController 가 보여준다)
  pages/themes/{방id}.html   → /themes/{방id}  테마별 소개 페이지 10개 (검색어에 맞춘 제목·설명·자주 묻는 질문·바로 들어가기)
  pages/themes/index.html    → /themes         테마 모음
  pages/about.html           → /about          서비스 소개 (만든 사람, 작동 원리, 개인정보 요약)
  pages/privacy.html         → /privacy        개인정보처리방침 (무엇을 어디에 얼마나 보관하는지)
  static/images/og/{방id}.jpg                  테마별 링크 미리보기 이미지 (1200×630)
  static/sitemap.xml                           위 페이지 전부

테마 내용을 바꾸려면 아래 THEMES 를 고치고 다시 실행한 뒤 결과 파일을 함께 커밋한다.
환경음·기본 음악은 DB(theme_ambient, theme_track) 내용을 사람이 읽기 좋게 옮겨 둔 것이다.
"""
import html
import json
import os
from datetime import date

from PIL import Image, ImageDraw, ImageFont

RESOURCES = os.path.join(os.path.dirname(__file__), '..', 'healing-room', 'src', 'main', 'resources')
ROOT = os.path.join(RESOURCES, 'static')   # 정적 파일 (이미지, sitemap)
PAGES = os.path.join(RESOURCES, 'pages')   # 소개 페이지 (SiteController 가 .html 없는 주소로 보여줌)
SITE = 'https://healing.kimseongmin.co.kr'
GA_ID = 'G-DYY0NFW8RR'  # Google Analytics (GA4). 바꾸면 static/index.html 의 태그도 같이 바꾼다
AUTHOR_NAME = '김성민'
AUTHOR_URL = 'https://github.com/kimseongmin0301'
# 만든 사람: 모든 소개 페이지의 구조화 데이터에 넣는다 (검색엔진·AI 가 누가 운영하는지 알 수 있게)
AUTHOR_LD = {'@type': 'Person', '@id': f'{SITE}/#author', 'name': AUTHOR_NAME, 'url': AUTHOR_URL, 'sameAs': [AUTHOR_URL]}
TODAY = date.today().isoformat()
FONT_BOLD = 'C:/Windows/Fonts/malgunbd.ttf'
FONT = 'C:/Windows/Fonts/malgun.ttf'

# room: 기본 방 id (방 주소 /{id}, 소개 페이지 /themes/{id}) / image: 배경 파일 이름
THEMES = [
    dict(room='cabin', image='cabin', name='불멍', title='불멍 · 겨울 오두막',
         keyword='불멍 사이트 · 모닥불·장작 타는 소리',
         what='불멍은 타오르는 불을 멍하니 바라보며 쉬는 것을 말해요.',
         lead='힐링룸의 불멍 방은 눈 내리는 겨울 오두막의 모닥불을 화면 가득 띄워 주는 무료 웹 공간이에요. 불꽃이 일렁이고 불씨가 피어오르며, 장작 타는 소리를 들으면서 같은 방 사람들과 음악을 함께 들어요.',
         scene=['불꽃이 일렁이고 방 안 불빛과 랜턴이 깜빡여요', '불씨가 위로 피어오르고 창밖에는 눈이 내려요', '화면을 누르면 장작이 들어가 불이 더 세져요'],
         sounds=['장작 타는 소리', '창밖 바람', '빗소리'],
         music='재즈 로파이, 북카페 재즈, 아늑한 오두막 재즈',
         good=['추운 날 따뜻한 분위기가 필요할 때', '공부·작업할 때 배경으로', '잠들기 전 마음을 가라앉힐 때'],
         faq=[('불멍 영상과 무엇이 다른가요?', '영상을 반복 재생하는 대신 브라우저에서 불꽃과 불씨가 계속 새로 움직이고, 장작 소리도 실시간으로 합성해서 끊김이나 반복 구간이 없어요. 같은 방 사람들과 같은 음악을 함께 듣고 채팅도 할 수 있어요.'),
              ('장작 소리만 켜 둘 수 있나요?', '네. 음악을 끄고 환경음 믹서에서 장작 소리만 올리면 돼요.')]),
    dict(room='sea', image='sea', name='바다멍', title='바다멍 · 달빛 바다',
         keyword='바다멍 · 밤바다 파도 소리',
         what='바다멍은 파도치는 바다를 멍하니 바라보며 쉬는 것을 말해요.',
         lead='힐링룸의 바다멍 방은 달빛이 비치는 잔잔한 밤바다를 보여 주는 무료 웹 공간이에요. 물결이 출렁이고 달빛이 반짝이는 동안 파도 소리와 바닷바람, 풀벌레 소리를 섞어 들을 수 있어요.',
         scene=['물결이 출렁이고 달빛 길 위로 빛이 반짝여요', '하늘에서 별이 깜빡여요', '하늘을 누르면 별똥별이 떨어져요'],
         sounds=['파도', '바닷바람', '풀벌레'],
         music='밤바다 로파이, 새벽 공부용 로파이, 잔잔한 수면 피아노',
         good=['잠이 안 오는 밤에', '집중이 필요한 새벽 공부 시간에', '복잡한 생각을 비우고 싶을 때'],
         faq=[('파도 소리를 수면용으로 켜 둬도 되나요?', '네. 음악을 끄고 파도 소리만 켜 두면 잔잔한 백색소음처럼 쓸 수 있어요. 소리는 브라우저에서 합성해서 반복 구간이 없어요.'),
              ('바다멍 방은 항상 열려 있나요?', '네. 기본 방이라 닫히지 않고 언제든 주소로 들어올 수 있어요.')]),
    dict(room='forest', image='forest', name='숲멍', title='숲멍 · 반딧불 시냇가',
         keyword='숲멍 · 시냇물 소리와 반딧불',
         what='숲멍은 숲과 나무, 물소리를 바라보며 쉬는 것을 말해요.',
         lead='힐링룸의 숲멍 방은 달빛 아래 시냇물이 흐르는 밤 숲을 보여 주는 무료 웹 공간이에요. 반딧불이 떠다니고 안개가 낮게 흐르는 동안 시냇물 소리와 풀벌레 소리를 들으며 쉴 수 있어요.',
         scene=['시냇물과 작은 폭포가 흐르고 물 위가 반짝여요', '반딧불이 천천히 떠다니며 깜빡여요', '화면을 누르면 반딧불이 한 무리 날아올라요'],
         sounds=['시냇물', '풀벌레', '산들바람'],
         music='새소리와 피아노, 숲속 재즈, 명상 앰비언트',
         good=['자연 소리가 그리울 때', '눈이 피로해 잠깐 쉬고 싶을 때', '명상이나 스트레칭할 때'],
         faq=[('시냇물 소리는 녹음인가요?', '아니요. 브라우저에서 물 흐르는 소리와 물방울 소리를 실시간으로 합성해요. 그래서 끝없이 이어지고 같은 구간이 반복되지 않아요.'),
              ('반딧불은 어떻게 움직이나요?', '캔버스 위에서 반딧불 하나하나가 천천히 떠다니며 숨 쉬듯 깜빡이고, 화면을 누르면 그 자리에서 날아올라요.')]),
    dict(room='rain', image='forest-rain', name='비멍', title='비멍 · 비 오는 숲',
         keyword='비멍 · 빗소리 백색소음',
         what='비멍은 내리는 비를 바라보며 빗소리를 듣고 쉬는 것을 말해요.',
         lead='힐링룸의 비멍 방은 처마 아래에서 비 오는 숲을 바라보는 무료 웹 공간이에요. 빗줄기와 시냇물, 폭포가 흐르고 멀리서 천둥이 울려요. 빗소리 백색소음을 켜 두고 공부하거나 잠들기 좋아요.',
         scene=['빗줄기가 내리고 젖은 바닥에 빗방울이 퍼져요', '시냇물과 폭포가 흐르고 처마 랜턴이 깜빡여요', '하늘을 누르면 번개가 쳐요'],
         sounds=['빗소리', '먼 천둥', '숲 바람'],
         music='빗소리와 피아노, 비 오는 날 로파이',
         good=['빗소리 백색소음이 필요할 때', '공부·독서 집중용으로', '비 오는 날 감성이 그리울 때'],
         faq=[('빗소리 백색소음으로 쓸 수 있나요?', '네. 음악을 끄고 빗소리만 켜 두면 돼요. 천둥은 따로 줄이거나 끌 수 있어요.'),
              ('천둥 소리가 너무 크면요?', '환경음 믹서에서 "먼 천둥" 슬라이더를 내리면 돼요. 설정은 브라우저에 기억돼요.')]),
    dict(room='cafe', image='rain-cafe', name='카페멍', title='카페멍 · 비 오는 창가',
         keyword='카페멍 · 비 오는 카페 재즈와 카페 소리',
         what='카페멍은 카페 창가에 앉아 바깥을 바라보며 쉬는 것을 말해요.',
         lead='힐링룸의 카페멍 방은 빗방울이 흐르는 카페 창가와 따뜻한 커피를 보여 주는 무료 웹 공간이에요. 창밖 도시 불빛이 번지고 커피에서 김이 오르는 동안, 빗소리와 도시 소리에 카페 재즈를 함께 들어요.',
         scene=['유리창에 맺힌 빗방울이 주르륵 흘러내려요', '커피잔에서 김이 모락모락 올라와요', '창을 누르면 그 자리에서 빗방울이 흘러요'],
         sounds=['창밖 빗소리', '도시 소리', '먼 천둥'],
         music='비 오는 날 카페 재즈, 커피숍 재즈',
         good=['카페에서 공부하는 느낌이 필요할 때', '재택근무 배경음으로', '비 오는 날 커피 한 잔과 함께'],
         faq=[('카페 소리로 공부할 수 있나요?', '네. 카페 재즈와 빗소리, 도시 소리를 섞어서 카페에서 공부하는 분위기를 만들 수 있어요. 각 소리의 크기는 따로 조절해요.'),
              ('다른 사람과 같은 음악을 듣나요?', '네. 같은 방에 있는 사람은 같은 곡을 같은 위치로 들어요. 누구나 노래를 검색해서 신청할 수 있어요.')]),
    dict(room='snow', image='snow-cabin', name='눈멍', title='눈멍 · 눈 내리는 산장',
         keyword='눈멍 · 오로라와 눈 내리는 풍경',
         what='눈멍은 소복이 내리는 눈을 바라보며 쉬는 것을 말해요.',
         lead='힐링룸의 눈멍 방은 오로라가 일렁이는 밤하늘 아래 눈 내리는 산장을 보여 주는 무료 웹 공간이에요. 랜턴 불빛과 달빛 호수를 바라보며 눈바람과 벽난로 소리를 들어요.',
         scene=['눈이 천천히 내리고 오로라가 일렁여요', '달빛이 언 호수 위에서 반짝여요', '화면을 누르면 바람이 불어 눈이 흩날려요'],
         sounds=['눈바람', '벽난로'],
         music='겨울 로파이, 아늑한 겨울 재즈',
         good=['겨울 감성이 필요할 때', '연말·크리스마스 분위기로', '조용히 생각을 정리하고 싶을 때'],
         faq=[('오로라도 움직이나요?', '네. 초록빛 오로라가 천천히 일렁이고, 눈과 랜턴 불빛도 계속 움직여요.'),
              ('여름에도 눈멍을 할 수 있나요?', '그럼요. 계절과 상관없이 언제든 들어와 시원한 겨울 풍경을 볼 수 있어요.')]),
    dict(room='sunset', image='sunset-beach', name='노을멍', title='노을멍 · 노을 해변',
         keyword='노을멍 · 해 지는 바다와 파도 소리',
         what='노을멍은 해 질 녘 노을을 바라보며 쉬는 것을 말해요.',
         lead='힐링룸의 노을멍 방은 해가 지는 열대 해변을 보여 주는 무료 웹 공간이에요. 파도가 밀려오고 노을빛이 물 위에서 반짝이며, 파도 소리와 갈매기 소리, 보사노바를 함께 들어요.',
         scene=['파도가 밀려오고 노을빛이 물 위에서 반짝여요', '갈매기가 날개를 펄럭이며 지나가요', '하늘을 누르면 갈매기가 날아가요'],
         sounds=['파도', '바닷바람', '갈매기'],
         music='보사노바, 선셋 칠아웃 라운지',
         good=['여행 기분을 내고 싶을 때', '퇴근 후 하루를 정리할 때', '여유로운 주말 오후에'],
         faq=[('노을멍 음악은 어떤 장르인가요?', '기본으로 보사노바와 선셋 칠아웃이 나와요. 원하는 노래를 검색해서 신청할 수도 있어요.'),
              ('갈매기 소리를 끌 수 있나요?', '네. 환경음 믹서에서 갈매기 슬라이더를 0으로 내리면 돼요.')]),
    dict(room='space', image='space-station', name='우주멍', title='우주멍 · 우주정거장',
         keyword='우주멍 · 우주정거장에서 보는 지구',
         what='우주멍은 우주와 별을 바라보며 쉬는 것을 말해요.',
         lead='힐링룸의 우주멍 방은 우주정거장 전망대 창밖으로 푸른 행성과 은하수를 보여 주는 무료 웹 공간이에요. 별이 반짝이고 행성 대기가 빛나는 동안 정거장의 낮은 울림과 우주의 화음을 들어요.',
         scene=['창밖의 별이 반짝이고 가끔 별똥별이 떨어져요', '행성 가장자리의 대기가 숨 쉬듯 빛나요', '밤 지역의 도시 불빛이 깜빡여요'],
         sounds=['정거장 소리', '우주의 화음'],
         music='우주 로파이, 스페이스 앰비언트',
         good=['깊이 몰입해서 작업할 때', '마음을 비우고 싶을 때', '우주 다큐멘터리 같은 분위기가 좋을 때'],
         faq=[('우주의 화음은 어떤 소리인가요?', '어떤 음이 겹쳐도 어울리는 5음계 화음이 천천히 나타났다 사라지는 소리예요. 브라우저에서 실시간으로 합성해요.'),
              ('집중용으로 좋나요?', '가사 없는 앰비언트 음악과 낮은 울림이라 공부·코딩 같은 집중 작업 배경으로 쓰기 좋아요.')]),
    dict(room='galaxy', image='space-nebula', name='별멍', title='별멍 · 깊은 우주',
         keyword='별멍 · 성운과 별을 바라보는 시간',
         what='별멍은 밤하늘의 별을 바라보며 쉬는 것을 말해요.',
         lead='힐링룸의 별멍 방은 보랏빛 성운과 고리 행성이 떠 있는 깊은 우주를 보여 주는 무료 웹 공간이에요. 밝은 별이 숨 쉬듯 빛나고 별가루가 천천히 도는 동안 우주 앰비언트를 들어요.',
         scene=['성운 사이 밝은 별이 숨 쉬듯 빛나요', '별가루가 그 별을 중심으로 천천히 돌아요', '화면을 누르면 별똥별이 떨어져요'],
         sounds=['우주의 화음', '깊은 울림'],
         music='딥 스페이스 앰비언트, 명상·수면 음악',
         good=['잠들기 전에', '명상할 때', '별 보러 갈 수 없는 날'],
         faq=[('수면용으로 써도 되나요?', '네. 잔잔한 앰비언트와 느린 움직임이라 잠들기 전에 켜 두기 좋아요.'),
              ('화면을 꺼도 소리가 나나요?', '브라우저 탭이 열려 있으면 소리는 계속 나요. 움직이는 효과만 끄고 싶다면 "효과 끄기"를 누르면 돼요.')]),
    dict(room='club', image='lounge-club', name='클럽', title='클럽 · 라운지',
         keyword='라운지 클럽 · 칠 하우스 음악',
         what='라운지 클럽은 잔잔한 하우스 음악과 네온 조명 속에서 쉬는 공간이에요.',
         lead='힐링룸의 클럽 방은 도시 야경이 보이는 라운지 클럽을 보여 주는 무료 웹 공간이에요. 네온 조명이 박자에 맞춰 밝아지고 빛줄기가 흔들리는 동안 라운지 비트와 칠 하우스 음악을 함께 들어요.',
         scene=['조명이 분당 100박 박자에 맞춰 밝아져요', '천장 링에서 색이 바뀌는 빛줄기가 흔들려요', '화면을 누르면 조명이 번쩍여요'],
         sounds=['라운지 비트', '도시 소리'],
         music='루프탑 라운지, 칠 하우스, 딥 하우스',
         good=['기분 전환이 필요할 때', '작업에 리듬을 주고 싶을 때', '친구들과 같은 음악을 들으며 이야기할 때'],
         faq=[('조명이 음악 박자를 따라가나요?', '조명은 분당 100박으로 움직이고, 환경음 "라운지 비트"도 같은 빠르기라 함께 켜면 박자가 맞아요.'),
              ('시끄럽지 않나요?', '라운지 비트는 옆방에서 들리는 것처럼 먹먹하게 만들어서 잔잔해요. 크기는 슬라이더로 조절해요.')]),
]

COMMON_FAQ = [
    ('힐링룸은 무료인가요?', '네, 무료이고 회원가입이나 로그인이 없어요.'),
    ('휴대폰에서도 되나요?', '네. 휴대폰과 태블릿 브라우저에서도 돼요. 브라우저 정책 때문에 화면을 한 번 누른 뒤에 소리가 나요.'),
]

esc = html.escape


def page(title, description, path, image, body, ld, extra_head=''):
    url = f'{SITE}{path}'
    ld['@graph'][0]['author'] = {'@id': f'{SITE}/#author'}
    ld['@graph'].append(AUTHOR_LD)
    return f'''<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{esc(title)}</title>
  <meta name="description" content="{esc(description)}">
  <link rel="canonical" href="{url}">
  <meta name="robots" content="index, follow, max-image-preview:large">
  <meta property="og:type" content="article">
  <meta property="og:site_name" content="힐링룸">
  <meta property="og:url" content="{url}">
  <meta property="og:title" content="{esc(title)}">
  <meta property="og:description" content="{esc(description)}">
  <meta property="og:image" content="{SITE}{image}">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta property="og:locale" content="ko_KR">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="theme-color" content="#0e0d12">
  <link rel="icon" href="/favicon.ico" sizes="any">
  <link rel="apple-touch-icon" href="/images/apple-touch-icon.png">
  <link rel="stylesheet" href="/css/pages.css">
  <script async src="https://www.googletagmanager.com/gtag/js?id={GA_ID}"></script>
  <script>window.dataLayer = window.dataLayer || []; function gtag(){{dataLayer.push(arguments);}} gtag('js', new Date()); gtag('config', '{GA_ID}');</script>
  <script type="application/ld+json">
{json.dumps(ld, ensure_ascii=False, indent=2)}
  </script>{extra_head}
</head>
<body>
  <!-- 이 파일은 scripts/seo_pages.py 로 만든다. 직접 고치지 말고 스크립트를 고친 뒤 다시 실행한다 -->
  <header class="top"><a class="logo" href="/"><img src="/images/mascot.webp" alt="" width="36" height="36">힐링룸</a>
    <nav><a href="/themes">테마</a><a href="/about">소개</a></nav></header>
{body}
  <footer class="foot">
    <p><a href="/">힐링룸</a> · <a href="/themes">테마 모음</a> · <a href="/about">서비스 소개</a> · <a href="/privacy">개인정보처리방침</a></p>
    <p>만든 사람 {AUTHOR_NAME} · <a href="{AUTHOR_URL}" rel="author">GitHub</a> · 최종 수정 {TODAY}</p>
  </footer>
</body>
</html>
'''


def faq_html(items):
    return '\n'.join(f'      <details><summary>{esc(q)}</summary><p>{esc(a)}</p></details>' for q, a in items)


def faq_ld(items):
    return {'@type': 'FAQPage', 'mainEntity': [
        {'@type': 'Question', 'name': q, 'acceptedAnswer': {'@type': 'Answer', 'text': a}} for q, a in items]}


def crumbs(*pairs):
    return {'@type': 'BreadcrumbList', 'itemListElement': [
        {'@type': 'ListItem', 'position': i + 1, 'name': name, 'item': f'{SITE}{path}'} for i, (name, path) in enumerate(pairs)]}


def og_image(t):
    """테마 배경 + 제목으로 링크 미리보기 이미지를 만든다"""
    w, h = 1200, 630
    bg = Image.open(os.path.join(ROOT, 'images', t['image'] + '.webp')).convert('RGB')
    s = max(w / bg.width, h / bg.height)
    bg = bg.resize((int(bg.width * s) + 1, int(bg.height * s) + 1), Image.LANCZOS)
    x, y = (bg.width - w) // 2, (bg.height - h) // 2
    img = bg.crop((x, y, x + w, y + h)).convert('RGBA')
    shade = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(shade)
    for row in range(h):  # 아래로 갈수록 어둡게
        d.line([(0, row), (w, row)], fill=(8, 8, 14, int(40 + 170 * max(0, row - h * 0.3) / (h * 0.7))))
    img = Image.alpha_composite(img, shade)
    d = ImageDraw.Draw(img)
    big, mid, small = ImageFont.truetype(FONT_BOLD, 92), ImageFont.truetype(FONT, 36), ImageFont.truetype(FONT, 26)
    subtitle = t['keyword'].split(' · ', 1)[-1]  # "카페멍 · 비 오는 카페…" → 이름은 위에 크게 있으니 뒷부분만
    for yy, text, font, fill in [(360, t['name'], big, (255, 255, 255)), (480, subtitle, mid, (240, 232, 220)),
                                 (548, '힐링룸 · healing.kimseongmin.co.kr', small, (255, 196, 135))]:
        tw = d.textlength(text, font=font)
        d.text(((w - tw) / 2 + 2, yy + 3), text, font=font, fill=(0, 0, 0, 150))
        d.text(((w - tw) / 2, yy), text, font=font, fill=fill)
    m = Image.open(os.path.join(ROOT, 'images', 'mascot.webp')).convert('RGBA')
    m.thumbnail((150, 150), Image.LANCZOS)
    img.alpha_composite(m, (w - m.width - 36, 30))
    os.makedirs(os.path.join(ROOT, 'images', 'og'), exist_ok=True)
    img.convert('RGB').save(os.path.join(ROOT, 'images', 'og', t['room'] + '.jpg'), quality=86, optimize=True)


def theme_page(t):
    path = f'/themes/{t["room"]}'
    faq = t['faq'] + COMMON_FAQ
    title = f'{t["keyword"]} | 힐링룸'
    description = (t['lead'][:150]).rstrip() + ('…' if len(t['lead']) > 150 else '')
    others = ''.join(f'<li><a href="/themes/{o["room"]}">{esc(o["name"])}</a></li>' for o in THEMES if o is not t)
    body = f'''  <main>
    <section class="hero" style="background-image: url('/images/{t["image"]}.webp')">
      <div class="hero-in">
        <p class="eyebrow">힐링룸 테마</p>
        <h1>{esc(t["title"])}</h1>
        <p class="lead">{esc(t["what"])} {esc(t["lead"])}</p>
        <a class="cta" href="/{t["room"]}">지금 {esc(t["name"])} 방 들어가기 →</a>
        <p class="note">무료 · 로그인 없음 · 휴대폰에서도 돼요</p>
      </div>
    </section>

    <div class="content">
      <section>
        <h2>화면에서는</h2>
        <ul>{''.join(f'<li>{esc(x)}</li>' for x in t["scene"])}</ul>
      </section>
      <section>
        <h2>들리는 소리</h2>
        <p>환경음은 녹음 파일 없이 브라우저에서 실시간으로 만들어서 끊기거나 반복되는 구간이 없어요. 슬라이더로 섞을 수 있어요.</p>
        <ul class="chips">{''.join(f'<li>{esc(x)}</li>' for x in t["sounds"])}</ul>
        <p>음악은 같은 방 사람들과 YouTube 를 같은 곡, 같은 위치로 함께 들어요. 신청곡이 없을 때는 <b>{esc(t["music"])}</b> 같은 음악이 나오고, 누구나 노래를 검색해서 신청할 수 있어요.</p>
      </section>
      <section>
        <h2>이럴 때 좋아요</h2>
        <ul>{''.join(f'<li>{esc(x)}</li>' for x in t["good"])}</ul>
      </section>
      <section>
        <h2>자주 묻는 질문</h2>
{faq_html(faq)}
      </section>
      <section class="cta-box">
        <p>{esc(t["name"])} 방은 항상 열려 있어요.</p>
        <a class="cta" href="/{t["room"]}">{esc(t["name"])} 방 들어가기 →</a>
      </section>
      <section>
        <h2>다른 테마</h2>
        <ul class="chips links">{others}</ul>
      </section>
    </div>
  </main>'''
    ld = {'@context': 'https://schema.org', '@graph': [
        {'@type': 'WebPage', '@id': f'{SITE}{path}#page', 'url': f'{SITE}{path}', 'name': title, 'description': description,
         'inLanguage': 'ko-KR', 'dateModified': TODAY, 'isPartOf': {'@id': f'{SITE}/#website'},
         'primaryImageOfPage': f'{SITE}/images/og/{t["room"]}.jpg',
         'about': {'@type': 'Thing', 'name': t['name'], 'description': t['what']},
         'potentialAction': {'@type': 'ViewAction', 'name': f'{t["name"]} 방 들어가기', 'target': f'{SITE}/{t["room"]}'}},
        crumbs(('힐링룸', '/'), ('테마', '/themes'), (t['name'], path)),
        faq_ld(faq)]}
    write_page(f'themes/{t["room"]}.html', page(title, description, path, f'/images/og/{t["room"]}.jpg', body, ld))
    og_image(t)


def themes_index():
    path = '/themes'
    title = '힐링룸 테마 모음 · 불멍 바다멍 비멍 카페멍 우주멍'
    description = '불멍, 바다멍, 숲멍, 비멍, 카페멍, 눈멍, 노을멍, 우주멍, 별멍, 라운지 클럽. 힐링룸의 10가지 테마 방을 소개해요. 모두 무료이고 로그인 없이 바로 들어가요.'
    cards = '\n'.join(f'''      <a class="card" href="/themes/{t["room"]}" style="background-image: url('/images/{t["image"]}.webp')">
        <span><b>{esc(t["name"])}</b>{esc(t["keyword"].split(" · ", 1)[-1])}</span></a>''' for t in THEMES)
    body = f'''  <main class="content">
    <h1>테마 모음</h1>
    <p class="lead">힐링룸에는 10가지 테마 방이 있어요. 테마마다 움직이는 배경, 어울리는 환경음, 기본 음악이 달라요. 마음에 드는 방을 골라 멍하니 쉬어 가세요.</p>
    <div class="cards">
{cards}
    </div>
  </main>'''
    ld = {'@context': 'https://schema.org', '@graph': [
        {'@type': 'CollectionPage', 'url': f'{SITE}{path}', 'name': title, 'description': description, 'inLanguage': 'ko-KR',
         'dateModified': TODAY, 'isPartOf': {'@id': f'{SITE}/#website'}},
        {'@type': 'ItemList', 'itemListElement': [
            {'@type': 'ListItem', 'position': i + 1, 'name': t['title'], 'url': f'{SITE}/themes/{t["room"]}'}
            for i, t in enumerate(THEMES)]},
        crumbs(('힐링룸', '/'), ('테마', path))]}
    write_page('themes/index.html', page(title, description, path, '/images/og-cat.jpg', body, ld))


def about():
    path = '/about'
    title = '힐링룸 소개 · 멍하니 쉬어 가는 무료 웹 공간'
    description = '힐링룸이 무엇인지, 같은 음악을 어떻게 함께 듣는지, 소리는 어떻게 만드는지, 개인정보는 어떻게 다루는지 정리했어요.'
    how = [('방 고르기', '첫 화면에서 테마 방을 누르거나, 방 만들기에서 이름과 테마를 골라 새 방을 만들어요.'),
           ('소리 켜기', '화면을 한 번 누르면 음악과 환경음이 나와요. 각각 따로 켜고 끌 수 있어요.'),
           ('음악 신청하기', '음악 창 아래 칸에 노래 제목을 검색하거나 YouTube 링크를 붙여 넣어 신청해요.'),
           ('함께 쉬기', '같은 방 사람들과 같은 음악을 듣고, 채팅으로 이야기해요. 방 주소를 친구에게 보낼 수도 있어요.')]
    body = f'''  <main class="content">
    <h1>힐링룸 소개</h1>
    <p class="lead">힐링룸은 화면을 켜두고 멍하니 쉬는 무료 웹 서비스예요. 불멍, 바다멍, 비 오는 숲, 우주 같은 테마 방에 들어가면 실사 배경이 천천히 움직이고 환경음이 흐르며, 같은 방 사람들과 YouTube 음악을 같은 곡·같은 위치로 함께 들어요. 회원가입과 로그인이 없어요.</p>

    <h2>이렇게 써요</h2>
    <ol class="steps">{''.join(f'<li><b>{esc(n)}</b> {esc(d)}</li>' for n, d in how)}</ol>

    <h2>숫자로 보는 힐링룸</h2>
    <ul>
      <li>테마 방 <b>10가지</b>, 모두 항상 열려 있어요</li>
      <li>브라우저에서 만드는 환경음 <b>12가지</b> (장작, 비, 파도, 시냇물, 풀벌레, 천둥, 바람, 도시, 갈매기, 정거장 소리, 우주의 화음, 라운지 비트)</li>
      <li>같은 음악을 듣는 사람 사이의 차이는 <b>최대 약 3초</b></li>
      <li>사용자가 만든 방은 아무도 없는 채로 <b>30분</b>이 지나면 닫혀요</li>
    </ul>

    <h2>같은 음악을 어떻게 함께 듣나요?</h2>
    <p>영상을 서로 주고받지 않고, 서버가 "이 곡이 몇 시에 시작했는지" 시각 하나만 알려 줘요. 각자 브라우저가 <b>지금 위치 = 현재 시각 − 시작 시각</b>을 계산해서 틀고, 컴퓨터마다 다른 시계는 서버 시각으로 보정해요. 5초마다 확인해서 3초 넘게 어긋나면 다시 맞춰요.</p>

    <h2>소리는 어떻게 만드나요?</h2>
    <p>환경음은 녹음 파일을 쓰지 않고 브라우저의 Web Audio 로 실시간 합성해요. 예를 들어 풀벌레 소리는 아주 짧은 "찍" 소리 여러 개를 높이와 박자, 거리가 다른 여러 마리로 겹쳐 만들어요. 그래서 반복 구간이 없고 파일을 내려받을 필요도 없어요.</p>

    <h2>개인정보</h2>
    <ul>
      <li>회원가입·로그인이 없어요. 이름, 이메일, 전화번호를 받지 않아요. 브라우저마다 무작위 방문자 번호를 만들어 구분해요.</li>
      <li>접속자 목록에는 닉네임과 IP 앞부분만(예: 203.0.*.*) 보여요. 앱은 IP 를 저장하지 않고, 서버 접속 기록에만 14일 남아요.</li>
      <li>채팅은 방마다 최근 100개만 보관하고, 사용자가 만든 방은 닫힐 때 함께 지워요.</li>
      <li>자세한 내용은 <a href="/privacy">개인정보처리방침</a>에 있어요.</li>
    </ul>

    <h2>만든 사람</h2>
    <p>개인 개발자가 만들고 운영해요. 소스 코드는 <a href="https://github.com/kimseongmin0301/healing_room">GitHub</a> 에 공개되어 있어요. 기술: Java 17, Spring Boot, PostgreSQL, Redis, WebSocket, Canvas, Web Audio, YouTube IFrame Player API.</p>

    <p><a class="cta" href="/">힐링룸 들어가기 →</a></p>
  </main>'''
    ld = {'@context': 'https://schema.org', '@graph': [
        {'@type': 'AboutPage', 'url': f'{SITE}{path}', 'name': title, 'description': description, 'inLanguage': 'ko-KR',
         'dateModified': TODAY, 'isPartOf': {'@id': f'{SITE}/#website'}},
        {'@type': 'HowTo', 'name': '힐링룸에서 쉬는 방법', 'step': [
            {'@type': 'HowToStep', 'position': i + 1, 'name': n, 'text': d} for i, (n, d) in enumerate(how)]},
        crumbs(('힐링룸', '/'), ('소개', path))]}
    write_page('about.html', page(title, description, path, '/images/og-cat.jpg', body, ld))


PRIVACY_SINCE = '2026-10-21'  # 내용을 바꾸면 날짜를 새로 적고, 아래 "바뀐 내용" 에 한 줄 남긴다


def privacy():
    path = '/privacy'
    title = '개인정보처리방침 · 힐링룸'
    description = '힐링룸이 어떤 정보를 어디에 얼마나 보관하는지, 다른 사람에게 무엇이 보이는지, 지우려면 어떻게 하는지 정리했어요.'
    rows = [
        ('방문자 번호 (무작위 문자열)', '같은 사람인지 구분 (방장, 투표, 내보내기, 도배 방지)', '브라우저 저장소·쿠키 hr_vid', '쿠키 400일, 직접 지울 때까지'),
        ('신청한 곡과 방문자 번호', '지난 신청곡, 넘기기 투표, 곡 추천', '서버 DB', '계속 (기록 초기화 때 삭제)'),
        ('방을 만든 사람의 방문자 번호', '한 사람이 방을 너무 많이 열지 못하게', '서버 DB', '계속'),
        ('채팅 메시지(붙인 이미지 포함), 닉네임', '방 채팅', '서버 Redis', '최근 100개. 기본 방은 마지막 메시지 후 24시간, 사용자 방은 닫힐 때(늦어도 7일)'),
        ('보낸 의견과 방문자 번호', '서비스 개선', '서버 DB', '운영자가 지울 때까지'),
        ('접속 IP 주소', '접속자 목록(앞부분만), 장난 대응, 위조 방지', '서버 메모리', '접속해 있는 동안만'),
        ('접속 IP 주소, 접속 시각, 브라우저 정보', '서버 오류·공격 확인', '웹 서버(nginx) 접속 기록', '14일'),
        ('욕설·음란어를 쓴 사람의 IP 주소·방문자 번호, 걸린 말(쓴 글 전체는 아님)', '30분 이용 제한', '서버 Redis', '30분 (지나면 자동으로 지워짐)'),
        ('본 페이지, 머문 시간, 기기·브라우저 종류, 대략의 지역, 들어온 경로, 방 입장·곡 신청 같은 이용 횟수',
         '방문 통계 (어떤 테마가 인기인지, 무엇을 고칠지)', 'Google Analytics (Google 서버)', '14개월 (Google Analytics 기본 보관 기간)'),
    ]
    local = [('chatNickname', '채팅 닉네임'), ('myRequests', '내가 신청한 곡 기록(최대 100곡, 내 추천에만 사용)'),
             ('musicOn · ambientOn · musicVolume', '소리 켜기·끄기, 음량'), ('musicView · chatCollapsed', '음악 창 크기, 채팅 창 접기'),
             ('ambientWithMusic', '음악과 함께 환경음 듣기')]
    body = f"""  <main class="content">
    <h1>개인정보처리방침</h1>
    <p class="lead">힐링룸은 회원가입과 로그인이 없어요. <b>이름, 이메일, 전화번호, 생년월일 같은 정보는 받지 않아요.</b> 그래도 서비스를 돌리려면 남는 정보가 있어서, 무엇을 어디에 얼마나 두는지 모두 적어 둬요.</p>

    <h2>1. 보관하는 정보</h2>
    <div class="table-wrap"><table>
      <thead><tr><th>무엇</th><th>왜</th><th>어디에</th><th>얼마나</th></tr></thead>
      <tbody>{''.join(f'<tr><td>{esc(a)}</td><td>{esc(b)}</td><td>{esc(c)}</td><td>{esc(d)}</td></tr>' for a, b, c, d in rows)}</tbody>
    </table></div>
    <p>방문자 번호는 브라우저마다 무작위로 만든 값이라 그것만으로는 누구인지 알 수 없어요. 광고는 없어요.
      방문 통계에는 Google Analytics 를 쓰고, 방문자 번호·닉네임·채팅 내용·곡 제목처럼 사람을 알아볼 수 있거나 직접 쓴 내용은 보내지 않아요.</p>

    <h2>2. 다른 사람에게 보이는 것</h2>
    <ul>
      <li>같은 방 사람에게: 닉네임, IP 앞부분(예: 203.0.*.*), 들어온 지 얼마나 됐는지, 방장 표시, 채팅 메시지</li>
      <li>방문자 번호와 전체 IP 는 다른 방문자에게 보이지 않아요. 전체 IP 는 장난·공격 대응을 위해 운영자만 볼 수 있어요.</li>
    </ul>
    <p><b>욕설·음란어 제한</b>: 채팅·닉네임·방 이름에 욕설이나 음란한 말이 있으면 그 IP 와 방문자 번호로 30분 동안 채팅·방 만들기·방 입장을 막아요.
      PC방·회사처럼 IP 를 같이 쓰는 곳에서는 옆 사람도 함께 막힐 수 있어요. 잘못 막혔다면 첫 화면 아래 의견 보내기로 알려 주세요.</p>

    <h2>3. 브라우저에 저장하는 것</h2>
    <p>아래 값은 서버가 아니라 <b>내 브라우저</b>에만 있어요. 브라우저의 사이트 데이터 삭제로 언제든 지울 수 있어요.</p>
    <ul>
      <li><b>hr_vid</b> (쿠키), <b>visitorId</b> (저장소): 방문자 번호</li>
      {''.join(f'<li><b>{esc(k)}</b>: {esc(v)}</li>' for k, v in local)}
      <li><b>_ga, _ga_*</b> (쿠키): Google Analytics 가 같은 브라우저의 재방문을 세는 무작위 번호 (최대 2년)</li>
    </ul>

    <h2>4. 함께 쓰는 외부 서비스</h2>
    <ul>
      <li><b>YouTube (Google)</b>: 음악은 YouTube 플레이어로 재생해요. 재생할 때 Google 이 자체 쿠키 등으로 정보를 모을 수 있고, 이는 <a href="https://policies.google.com/privacy?hl=ko" rel="noopener">Google 개인정보처리방침</a>을 따라요. 노래를 검색하면 검색어만 YouTube 로 보내요(방문자 정보는 보내지 않음).</li>
      <li><b>Google Analytics (Google)</b>: 방문 통계를 위해 페이지를 볼 때 위 1번 표의 정보가 Google 로 가요.
        <a href="https://policies.google.com/technologies/partner-sites?hl=ko" rel="noopener">Google 이 이 정보를 쓰는 방법</a>.
        원하지 않으면 브라우저에서 쿠키를 막거나 <a href="https://tools.google.com/dlpage/gaoptout?hl=ko" rel="noopener">Google Analytics 차단 부가기능</a>을 쓰면 돼요. 막아도 힐링룸은 그대로 쓸 수 있어요.</li>
      <li><b>카카오페이</b>: 서버비 후원 버튼의 QR 이나 링크를 직접 눌렀을 때만 카카오페이로 이동해요.</li>
      <li>그 밖에 다른 회사에 정보를 주거나 팔지 않아요.</li>
    </ul>

    <h2>5. 지우고 싶을 때</h2>
    <ul>
      <li>내 브라우저에 있는 정보: 브라우저 설정에서 이 사이트의 쿠키·사이트 데이터를 지우면 돼요.</li>
      <li>서버에 남은 채팅·의견·신청곡 기록: 첫 화면 아래 <b>의견 보내기</b>나 아래 연락처로 방 이름, 대략의 시각, 내용을 알려 주면 찾아서 지워요.</li>
    </ul>

    <h2>6. 안전하게 지키는 방법</h2>
    <ul>
      <li>모든 연결은 HTTPS 로 암호화해요.</li>
      <li>DB 와 Redis 는 비밀번호가 있는 이 서비스 전용 계정으로만 쓰고, 운영자 화면은 로그인과 관리자 키 두 단계로 잠가 둬요.</li>
      <li>채팅·의견은 글자 그대로만 보여 주고 HTML 로 해석하지 않아요.</li>
    </ul>

    <h2>7. 만 14세 미만</h2>
    <p>나이를 묻거나 아동의 개인정보를 따로 모으지 않아요.</p>

    <h2>8. 운영자와 연락처</h2>
    <p>개인정보 보호 책임자: <b>{AUTHOR_NAME}</b> (개인 운영)<br>
      연락: 첫 화면 아래 <b>의견 보내기</b> 또는 <a href="{AUTHOR_URL}" rel="author">GitHub</a></p>

    <h2>9. 바뀐 내용</h2>
    <ul>
      <li>{PRIVACY_SINCE} 욕설·음란어 30분 이용 제한 추가</li>
      <li>2026-10-19 방문 통계(Google Analytics) 추가</li>
      <li>2026-10-16 처음 만들고 시행</li>
    </ul>

    <p><a class="cta" href="/">힐링룸 들어가기 →</a></p>
  </main>"""
    ld = {'@context': 'https://schema.org', '@graph': [
        {'@type': 'WebPage', 'url': f'{SITE}{path}', 'name': title, 'description': description, 'inLanguage': 'ko-KR',
         'dateModified': PRIVACY_SINCE, 'isPartOf': {'@id': f'{SITE}/#website'}},
        crumbs(('힐링룸', '/'), ('개인정보처리방침', path))]}
    write_page('privacy.html', page(title, description, path, '/images/og-cat.jpg', body, ld))


def sitemap():
    urls = [('/', '1.0', 'weekly'), ('/themes', '0.9', 'weekly'), ('/about', '0.6', 'monthly'), ('/privacy', '0.3', 'yearly')]
    urls += [(f'/themes/{t["room"]}', '0.8', 'monthly') for t in THEMES]
    urls += [('/llms.txt', '0.3', 'monthly')]
    body = '\n'.join(f'''  <url>
    <loc>{SITE}{p}</loc>
    <lastmod>{TODAY}</lastmod>
    <changefreq>{c}</changefreq>
    <priority>{pr}</priority>
  </url>''' for p, pr, c in urls)
    write('/sitemap.xml', f'''<?xml version="1.0" encoding="UTF-8"?>
<!-- scripts/seo_pages.py 로 만든다. 방 주소(/방id)는 첫 화면과 같은 내용이라 소개 페이지들을 올린다 -->
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
{body}
</urlset>
''')


def write_page(path, text):
    """소개 페이지는 resources/pages/ 에 둔다 (정적 파일로 바로 내보내지 않고 SiteController 가 깔끔한 주소로 보여줌)"""
    save(os.path.join(PAGES, path), text, 'pages/' + path)


def write(path, text):
    save(os.path.join(ROOT, path.lstrip('/')), text, 'static' + path)


def save(full, text, label):
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print('wrote', label)


if __name__ == '__main__':
    for theme in THEMES:
        theme_page(theme)
    themes_index()
    about()
    privacy()
    sitemap()
