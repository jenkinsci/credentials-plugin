Jenkins Credentials Plugin
=====================

Read more: [http://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin](http://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)

Documentation
=============

[![Documentation](docs/images/manual.png)](docs/)
[ documentation here](docs/)

Bug Reports
===========

![NO GITHUB ISSUES](https://i.imgflip.com/18d2ou.jpg)

File bug reports here: [In the JENKINS jira project with component `credentials-plugin`](https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20status%20in%20(Open%2C%20%22In%20Progress%22%2C%20Reopened%2C%20%22In%20Review%22)%20AND%20component%20%3D%20credentials-plugin)

**DO NOT USE GITHUB ISSUES** Any GitHub Issues will be ignored and closed as soon as they are spotted.

Development
===========

Start the local Jenkins instance:

    mvn hpi:run


How to install
--------------

Run

	mvn clean package

to create the plugin .hpi file.


To install:

1. copy the resulting ./target/credentials.hpi file to the $JENKINS_HOME/plugins directory. Don't forget to restart Jenkins afterwards.

2. or use the plugin management console (http://example.com:8080/pluginManager/advanced) to upload the hpi file. You have to restart Jenkins in order to find the pluing in the installed plugins list.


Plugin releases
---------------

	mvn release:prepare release:perform -B


License
-------

	(The MIT License)

    Copyright © 2011-2012, CloudBees, Inc., Stephen Connolly.

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.
