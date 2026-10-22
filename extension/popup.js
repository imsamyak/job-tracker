// The popup shows the JobTracker app. The app cannot see your browser tabs, so when it asks to "read this page"
// this script reads the open tab (only because you clicked the extension) and hands the text back to the app.
const ORIGIN = 'http://localhost:8080'
const frame = document.getElementById('app')

window.addEventListener('message', async (event) => {
  if (event.source !== frame.contentWindow || !event.data || event.data.type !== 'jt-read-page') return
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
    const [result] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: () => ({ url: location.href, title: document.title, text: document.body ? document.body.innerText.slice(0, 30000) : '' }),
    })
    frame.contentWindow.postMessage({ type: 'jt-page', ...result.result }, ORIGIN)
  } catch (e) {
    frame.contentWindow.postMessage({ type: 'jt-page', error: 'This page cannot be read.' }, ORIGIN)
  }
})
