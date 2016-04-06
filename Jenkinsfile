/*
apt install gnome-doc-utils gettext dpkg-dev debhelper
*/
node {
  stage 'Checkout'
  checkout ([
    $class: 'GitSCM',
    userRemoteConfigs: [[url: 'https://github.com/bozaro/git-as-svn.git']],
    extensions: [[$class: 'CleanCheckout']]
  ])

  stage 'Build'
  sh './gradlew assembleDist'
  archive 'build/distributions/*'

  stage 'Test'
  sh './gradlew check -PtestIgnoreFailures=true'
  step ([
    $class: 'JUnitResultArchiver',
    testResults: '**/build/test-results/*.xml'
  ])
}
