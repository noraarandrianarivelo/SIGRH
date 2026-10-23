// Besoins en personnel : traitements existants, navigation commune et CSRF.
const STATUTS = { BROUILLON: 'Brouillon', ATTENTE_RH: 'En attente RH', ATTENTE_FINANCE: 'En attente Finance',
        ATTENTE_DIRECTION: 'En attente Direction', A_CORRIGER: 'À corriger', VALIDEE: 'Validée', REJETEE: 'Rejetée' };
    const DECISIONS = { APPROUVEE: 'Approuver', A_CORRIGER: 'Demander une correction', REJETEE: 'Rejeter' };
    const MOTIFS = { NOUVEAU_POSTE: 'Nouveau poste', REMPLACEMENT: 'Remplacement', NOUVEAU_SERVICE: 'Nouveau service', AUTRE: 'Autre' };

    let utilisateur = null;
    let postes = [];          // postes du département choisi
    let demandes = [];        // dernière liste reçue
    let idEnModification = null;

    const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const nombre = n => Number(n).toLocaleString('fr-FR');
    const date = s => s ? s.split('-').reverse().join('/') : '';
    const dateHeure = s => s ? new Date(s).toLocaleString('fr-FR') : '';

    // Appel JSON : renvoie les données ou lève une Error avec le message du serveur.
    async function api(url, options = {}) {
        const csrfToken = document.querySelector('meta[name="_csrf"]').content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
        const res = await fetch(url, {
            ...options,
            headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken, ...options.headers }
        });
        if (res.redirected && res.url.includes('/login')) { window.location.href = '/login'; throw new Error('Session expirée'); }
        const texte = await res.text();
        let data = null;
        try { data = texte ? JSON.parse(texte) : null; } catch (e) { /* réponse non JSON */ }
        if (!res.ok) throw new Error(data && data.message ? data.message : 'Erreur ' + res.status);
        return data;
    }

    async function demarrer() {
        try {
            utilisateur = await api('/api/auth/current-user');
            if (utilisateur.role === 'ROLE_MANAGER' || utilisateur.role === 'ROLE_DIRECTION') {
                document.getElementById('sectionManager').hidden = false;
                await chargerDepartements();
            }
            await chargerDemandes();
        } catch (err) { notifier(err.message, true); }
    }

    // ---------- Formulaire ----------
    async function chargerDepartements() {
        let deps = await api('/api/departements');
        // Un manager ne peut créer que pour son département (RG) ; la Direction choisit librement.
        if (utilisateur.role === 'ROLE_MANAGER') deps = deps.filter(d => d.id === utilisateur.departementId);
        document.getElementById('departementId').innerHTML = deps.map(d => `<option value="${d.id}">${esc(d.libelle)}</option>`).join('');
        await changerDepartement();
    }

    async function chargerPostes() {
        const id = document.getElementById('departementId').value;
        postes = id ? await api('/api/postes/departement/' + id) : [];
    }

    async function changerDepartement() {
        await chargerPostes();
        document.getElementById('lignes').innerHTML = '';
        ajouterLigne();
    }

    function ajouterLigne(l = {}) {
        const options = postes.map(p => `<option value="${p.id}" ${l.posteId === p.id ? 'selected' : ''}>${esc(p.libelle)}</option>`).join('');
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><select class="l-poste" onchange="majLibelle(this)"><option value="">Nouveau poste à créer...</option>${options}</select>
                <input class="l-libelle" placeholder="Libellé du nouveau poste" value="${esc(l.libellePostePropose || '')}"></td>
            <td><input type="number" class="l-effectif" min="1" value="${l.effectif || 1}" style="width:70px"></td>
            <td><input type="number" class="l-budget" min="0" step="1000" value="${l.budgetMensuelUnitaire ?? ''}"></td>
            <td><button class="button button--secondary" type="button" onclick="this.closest('tr').remove()">Retirer</button></td>`;
        document.getElementById('lignes').appendChild(tr);
        majLibelle(tr.querySelector('.l-poste'));
    }

    // Le champ « libellé » ne sert que si aucun poste existant n'est choisi.
    function majLibelle(select) {
        select.closest('tr').querySelector('.l-libelle').style.display = select.value ? 'none' : '';
    }

    function lireLignes() {
        return [...document.querySelectorAll('#lignes tr')].map(tr => {
            const poste = tr.querySelector('.l-poste').value;
            const budget = tr.querySelector('.l-budget').value;
            return {
                posteId: poste ? Number(poste) : null,
                libellePostePropose: tr.querySelector('.l-libelle').value,
                effectif: Number(tr.querySelector('.l-effectif').value),
                budgetMensuelUnitaire: budget === '' ? null : Number(budget)
            };
        });
    }

    document.getElementById('formDemande').addEventListener('submit', async e => {
        e.preventDefault();
        const payload = {
            departementId: Number(document.getElementById('departementId').value),
            typeMotif: document.getElementById('typeMotif').value,
            motif: document.getElementById('motif').value,
            dateArriveeSouhaitee: document.getElementById('dateArrivee').value || null,
            lignes: lireLignes()
        };
        try {
            await api(idEnModification ? '/api/recrutement/demandes/' + idEnModification : '/api/recrutement/demandes',
                { method: idEnModification ? 'PUT' : 'POST', body: JSON.stringify(payload) });
            notifier('Brouillon enregistré. Cliquez sur « Soumettre » dans la liste pour l\'envoyer.', false);
            await annulerModification();
            await chargerDemandes();
        } catch (err) { notifier(err.message, true); }
    });

    async function modifier(id) {
        const d = demandes.find(x => x.id === id);
        idEnModification = id;
        document.getElementById('titreForm').textContent = 'Modifier la demande n° ' + id;
        document.getElementById('btnEnregistrer').textContent = 'Enregistrer les modifications';
        document.getElementById('btnAnnuler').hidden = false;
        document.getElementById('departementId').value = d.departementId;
        await chargerPostes();
        document.getElementById('typeMotif').value = d.typeMotif;
        document.getElementById('motif').value = d.motif;
        document.getElementById('dateArrivee').value = d.dateArriveeSouhaitee || '';
        document.getElementById('lignes').innerHTML = '';
        d.lignes.forEach(l => ajouterLigne({ posteId: l.posteId, libellePostePropose: l.posteACreer ? l.poste : '', effectif: l.effectif, budgetMensuelUnitaire: l.budgetMensuelUnitaire }));
        window.scrollTo(0, 0);
    }

    async function annulerModification() {
        idEnModification = null;
        document.getElementById('formDemande').reset();
        document.getElementById('titreForm').textContent = 'Nouvelle demande de recrutement';
        document.getElementById('btnEnregistrer').textContent = 'Enregistrer le brouillon';
        document.getElementById('btnAnnuler').hidden = true;
        await changerDepartement();
    }

    // ---------- Liste et actions ----------
    async function chargerDemandes() {
        try {
            demandes = await api('/api/recrutement/demandes');
            afficherDemandes();
        } catch (err) { notifier(err.message, true); }
    }

    function afficherDemandes() {
        const tbody = document.querySelector('#tableDemandes tbody');
        tbody.innerHTML = demandes.length ? '' : '<tr><td colspan="8">Aucune demande.</td></tr>';
        demandes.forEach(d => {
            const lignes = d.lignes.map(l => `<li>${esc(l.poste)}${l.posteACreer ? ' <em>(à créer)</em>' : ''}
                x ${l.effectif} à ${nombre(l.budgetMensuelUnitaire)} Ar
                ${l.grilleMin != null ? `<small>(grille ${nombre(l.grilleMin)} - ${nombre(l.grilleMax)})</small>` : ''}
                ${l.conforme ? '' : '<strong class="alerte">hors grille</strong>'}</li>`).join('');
            const histo = d.historique.length
                ? `<details><summary>Historique (${d.historique.length})</summary><ul>`
                  + d.historique.map(h => `<li>${dateHeure(h.date)} - ${h.etape} - ${esc(h.validateur)} : <strong>${h.decision}</strong>${h.commentaire ? ' - ' + esc(h.commentaire) : ''}</li>`).join('')
                  + '</ul></details>' : '';
            let actions = '';
            if (d.peutModifier) actions += `<button class="button button--secondary" onclick="modifier(${d.id})">Modifier</button><button class="button" onclick="soumettre(${d.id})">Soumettre</button>`;
            d.decisionsPossibles.forEach(dec => actions += `<button class="d-${dec}" onclick="decider(${d.id}, '${dec}')">${DECISIONS[dec]}</button>`);

            const tr = document.createElement('tr');
            tr.innerHTML = `<td>${d.id}</td><td>${esc(d.demandeur)}</td><td>${esc(d.departement)}</td>
                <td>${MOTIFS[d.typeMotif]}<br><small>${esc(d.motif)}</small></td><td><ul>${lignes}</ul></td>
                <td>${date(d.dateArriveeSouhaitee)}</td><td class="statut">${STATUTS[d.statut]}${histo}</td><td>${actions}</td>`;
            tbody.appendChild(tr);
        });
    }

    async function soumettre(id) { 
        if (!confirm('Soumettre cette demande ? Elle ne sera plus modifiable.')) return;
        try { await api('/api/recrutement/demandes/' + id + '/soumettre', { method: 'PUT' }); await chargerDemandes(); }
        catch (err) { notifier(err.message, true); }
    }

    async function decider(id, decision) {
        let commentaire = null;
        if (decision === 'APPROUVEE') {
            if (!confirm('Approuver la demande n° ' + id + ' ?')) return;
        } else {
            commentaire = prompt('Motif (obligatoire) :');
            if (commentaire === null) return;
        }
        try {
            await api('/api/recrutement/demandes/' + id + '/decision', { method: 'POST', body: JSON.stringify({ decision, commentaire }) });
            await chargerDemandes();
        } catch (err) { notifier(err.message, true); }
    }

    function notifier(message, erreur) {
        const zone = document.getElementById('demande-feedback');
        zone.textContent = message;
        zone.className = erreur ? 'error-message' : 'success-message';
        zone.setAttribute('role', erreur ? 'alert' : 'status');
        zone.hidden = false;
        zone.scrollIntoView({ block: 'nearest' });
    }

    demarrer();
