/* Navigation commune : le serveur reste la seule source des droits d'accès. */
(() => {
    'use strict';

    document.body.classList.add('js-ready');
    const sidebar = document.getElementById('app-sidebar');
    const toggle = document.querySelector('[data-sidebar-toggle]');

    function setSidebar(open) {
        document.body.classList.toggle('sidebar-open', open);
        toggle?.setAttribute('aria-expanded', String(open));
        toggle?.setAttribute('aria-label', open ? 'Fermer le menu principal' : 'Ouvrir le menu principal');
        if (sidebar) {
            sidebar.inert = !open && window.matchMedia('(max-width: 960px)').matches;
        }
    }

    toggle?.addEventListener('click', () => setSidebar(!document.body.classList.contains('sidebar-open')));
    document.querySelectorAll('[data-sidebar-close]').forEach(button => {
        button.addEventListener('click', () => {
            setSidebar(false);
            toggle?.focus();
        });
    });
    window.matchMedia('(max-width: 960px)').addEventListener('change', () => setSidebar(false));
    setSidebar(false);

    // Recherche d'écran avec clavier, gestion native du focus par <dialog> et Échap.
    const dialog = document.getElementById('screen-search');
    const input = document.getElementById('screen-search-input');
    const openSearch = document.querySelector('[data-search-open]');
    const results = [...document.querySelectorAll('[data-screen-result]')];
    const normalize = value => value.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

    function filterResults() {
        const query = normalize(input.value.trim());
        results.forEach(result => {
            result.hidden = !normalize(result.textContent).includes(query);
        });
        document.getElementById('screen-search-empty').hidden = results.some(result => !result.hidden);
    }

    function showSearch() {
        if (!dialog || dialog.open) return;
        input.value = '';
        filterResults();
        dialog.showModal();
        input.focus();
    }

    if (dialog && openSearch && typeof dialog.showModal === 'function') {
        openSearch.hidden = false;
        openSearch.addEventListener('click', showSearch);
        input.addEventListener('input', filterResults);
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                results.find(result => !result.hidden)?.click();
            }
        });
        document.querySelector('[data-search-close]').addEventListener('click', () => dialog.close());
        dialog.addEventListener('click', event => {
            const bounds = dialog.getBoundingClientRect();
            if (event.clientX < bounds.left || event.clientX > bounds.right
                    || event.clientY < bounds.top || event.clientY > bounds.bottom) {
                dialog.close();
            }
        });
    }

    document.addEventListener('keydown', event => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && dialog) {
            event.preventDefault();
            showSearch();
        }
        if (event.key === 'Escape' && document.body.classList.contains('sidebar-open')) {
            setSidebar(false);
            toggle?.focus();
        }
    });

    // Une seule action explicite permet de voir ou masquer le mot de passe saisi.
    document.querySelectorAll('[data-password-toggle]').forEach(button => {
        button.addEventListener('click', () => {
            const password = document.getElementById(button.dataset.passwordToggle);
            const visible = password.type === 'password';
            password.type = visible ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(visible));
            button.setAttribute('aria-label', visible ? 'Masquer le mot de passe' : 'Afficher le mot de passe');
        });
    });
})();
