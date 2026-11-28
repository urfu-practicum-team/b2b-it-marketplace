const jobs = [
  {name:'Интеграция 1С с интернет-магазином', description:'Синхронизировать каталог и остатки между складской базой и сайтом.', budget:250000, due:'2026-12-20', criteria:'Каталог загружается из 1С, остатки в магазине совпадают с тестовой выгрузкой.'},
  {name:'Отчёт по продажам для сети магазинов', description:'Собрать выручку, возвраты и средний чек по филиалам в одном отчёте.', budget:95000, due:'2026-12-24', criteria:'Итоги за тестовый месяц совпадают с выгрузкой бухгалтерии.'},
  {name:'Перенос базы знаний в корпоративный портал', description:'Перенести инструкции и сохранить поиск по разделам.', budget:160000, due:'2027-01-18', criteria:'Все статьи доступны по прежним ссылкам и находятся по названию.'}
]
const catalog = document.querySelector('#catalog')
const creation = document.querySelector('#creation')
const search = document.querySelector('#search')
const form = document.querySelector('#publish')
const notice = document.querySelector('#notice')
const due = form.elements.due
const rubles = new Intl.NumberFormat('ru-RU',{style:'currency',currency:'RUB',minimumFractionDigits:0})
const dates = new Intl.DateTimeFormat('ru-RU',{day:'numeric',month:'long',year:'numeric'})
function element(tag, value) {
  const node = document.createElement(tag)
  node.textContent = value
  return node
}
function showJobs() {
  const query = search.value.trim().toLocaleLowerCase('ru')
  const matches = jobs.filter(job => (job.name+' '+job.description).toLocaleLowerCase('ru').includes(query))
  const list = document.querySelector('#jobs')
  list.replaceChildren()
  document.querySelector('#count').textContent = matches.length
  for (const job of matches) {
    const card = document.createElement('article')
    card.className = 'job'
    const terms = document.createElement('dl')
    for (const [label,value] of [['Бюджет до',rubles.format(job.budget)],['Срок до',dates.format(new Date(job.due+'T12:00:00'))]]) {
      const item = document.createElement('div')
      item.append(element('dt',label),element('dd',value))
      terms.append(item)
    }
    const details = document.createElement('details')
    details.append(element('summary','Условия приёмки'),element('p',job.criteria))
    card.append(element('h3',job.name),element('p',job.description),terms,details)
    list.append(card)
  }
  if (!matches.length) list.append(element('p','Совпадений нет. Попробуйте другое название или очистите поиск.'))
}
function updateDeadline() {
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate()+1)
  due.min = [tomorrow.getFullYear(), String(tomorrow.getMonth()+1).padStart(2,'0'), String(tomorrow.getDate()).padStart(2,'0')].join('-')
}
function route() {
  const writing = location.hash === '#new'
  catalog.hidden = writing
  creation.hidden = !writing
  const heading = (writing ? creation : catalog).querySelector('h1')
  document.title = heading.textContent+' · ИТ-маркетплейс'
  if (writing) updateDeadline()
  heading.tabIndex = -1
  heading.focus({preventScroll:true})
  window.scrollTo(0,0)
}
form.addEventListener('input', event => event.target.setCustomValidity(''))
form.addEventListener('submit', event => {
  event.preventDefault()
  updateDeadline()
  for (const key of ['name','description','criteria']) {
    const field = form.elements.namedItem(key)
    field.setCustomValidity(field.value.trim() ? '' : 'Заполните поле текстом.')
  }
  if (!form.reportValidity()) return
  const job = Object.fromEntries(new FormData(form))
  for (const key of ['name','description','criteria']) job[key] = job[key].trim()
  jobs.unshift(job)
  search.value = ''
  showJobs()
  form.reset()
  notice.textContent = 'Задание опубликовано в демокаталоге.'
  location.hash = 'catalog'
})
search.addEventListener('input',showJobs)
window.addEventListener('hashchange',route)
showJobs()
route()
