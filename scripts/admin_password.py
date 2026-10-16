"""
관리자 비밀번호(문장)를 해시로 바꾼다. 원문은 어디에도 저장하지 않는다.

  python scripts/admin_password.py

입력한 문장은 화면에 보이지 않고, 아래 형식의 한 줄만 출력한다. 그 줄을 .env 의 ADMIN_PASSWORD_HASH= 뒤에 붙여넣는다.
  pbkdf2_sha256$반복횟수$소금$해시

서버(AdminLoginService.verifyPassword)가 같은 방식(PBKDF2-HMAC-SHA256)으로 확인한다. 파이썬 기본 기능만 쓴다.
"""
import base64
import getpass
import hashlib
import os

ITERATIONS = 310_000  # OWASP 권장 수준


def make_hash(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, ITERATIONS, dklen=32)
    return 'pbkdf2_sha256${}${}${}'.format(ITERATIONS, base64.b64encode(salt).decode(), base64.b64encode(digest).decode())


if __name__ == '__main__':
    first = getpass.getpass('관리자 비밀번호 문장 (화면에 안 보여요): ')
    if len(first) < 12:
        raise SystemExit('12자 이상의 문장으로 정해 주세요. 예: 새벽 세시에 불멍하는 졸린 고양이')
    if getpass.getpass('한 번 더 입력: ') != first:
        raise SystemExit('두 번 입력한 문장이 달라요. 다시 실행해 주세요.')
    print()
    print('아래 한 줄을 .env 의 ADMIN_PASSWORD_HASH= 뒤에 붙여넣으세요:')
    print(make_hash(first))
