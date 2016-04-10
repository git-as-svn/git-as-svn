/*
apt install gnome-doc-utils gettext dpkg-dev debhelper
*/
node {
  stage 'Checkout'
  checkout scm
  sh 'git reset --hard'
  sh 'git clean -ffdx'

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
