FROM ubuntu:16.04

RUN apt-get update

# Set en_US.UTF-8 locale
RUN apt-get install -y locales
ENV LANG en_US.UTF-8
RUN locale-gen en_US.UTF-8

# Bukd environment
RUN apt-get install -y default-jdk gnome-doc-utils gettext build-essential fakeroot debhelper git subversion

ENV HOME /var/jenkins_home
