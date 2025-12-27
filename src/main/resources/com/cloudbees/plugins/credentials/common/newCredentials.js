Behaviour.specify(
  ".jenkins-radio",
  "credentials-domains-loading",
  5000,
  function (e) {
    e.addEventListener('change', () => {
      renderOnDemand(
        document.querySelector('.credentials-domain')
      )
    })
  }
)
