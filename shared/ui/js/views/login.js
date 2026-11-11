'use strict';
window.Views = window.Views || {};
window.Views.login = {
  show: function (opts) {
    opts = opts || {};
    const root = document.getElementById('login-root');
    root.innerHTML = '';
    const card = document.createElement('div');
    card.className = 'container py-5';
    card.innerHTML = `
      <div class="row justify-content-center">
        <div class="col-md-6 col-lg-5">
          <div class="card shadow-sm">
            <div class="card-body p-4">
              <h3 class="mb-3">Вход в МЭШ</h3>
              <p class="text-secondary small">Вставьте токен МЭШ (или скопированный текст страницы целиком). Токен хранится только в вашем браузере.</p>
              <div class="mb-3">
                <textarea id="login-token" class="form-control" rows="4" placeholder="Вставьте токен или текст страницы..."></textarea>
              </div>
              <div class="d-flex gap-2">
                <button id="login-submit" class="btn btn-primary">Войти</button>
              </div>
              <div id="login-error" class="text-danger small mt-2"></div>
            </div>
          </div>
        </div>
      </div>`;
    root.appendChild(card);
    root.classList.remove('d-none');
    const btn = document.getElementById('login-submit');
    btn.onclick = async () => {
      const raw = document.getElementById('login-token').value;
      const err = document.getElementById('login-error');
      err.textContent = '';
      btn.disabled = true;
      try {
        const data = await AppAPI.api('/api/sign-in', { method: 'POST', body: JSON.stringify({ token: raw }) });
        if (opts.onSuccess) opts.onSuccess({ token: AppAPI.getToken(), profile: data.profile, children: data.children, activeStudentId: data.activeStudentId });
      } catch (e) {
        err.textContent = e.message;
      } finally {
        btn.disabled = false;
      }
    };
  }
};