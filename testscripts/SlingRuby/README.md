Ruby REST API's
===============

This is a REST Client Library for Sakai K2 written in Ruby.

It should run on any platform with ruby installed, however it may need some
extra components.

To get these make certain you have a Ruby installation with rubygems installed.
Once you have this try running one of the scripts. eg

    create-user.rb testuser

If you get any errors you may need to add some extra components. to do this use ruby bundler.

If you don't have bundler installed you can get it by running

    gem install bundler

You can then install the ruby gem dependencies by running

    bundle install

If you are using Windows XP, the following instruction may help.

How to install curl, ruby, and the rubygem libraries (curb and json) onto a windows xp machine.
===============================================================================================
Please note that more up to date documentation may be available on confluence.

1) installing curl
==================
Go to http://curl.haxx.se/download.html , scroll down the list of packages looking for the "Win32 - Generic" section. Download the following package:

    Package       Version Type    SSL Provider
    Win32 2000/XP 7.21.1  libcurl ssl Gunter Knauf

Install the program by unzipping the file into a directory that has NO spaces in the directory path (ie do not install in `C:\Program Files`). Best to unzip the file into a directory something like `C:\curl`

Then add something like the following to your Path: `C:\curl\curl-7.21.1-devel-mingw32\bin`

It might be a good idea to add `C:\curl\curl-7.21.1-devel-mingw32\bin` to the front of you Path statement, as it contains a recent version of libeay32.dll, and some of your other applications may contain older versions.
Otherwise you might see the following error when attempting to run the integration tests `The procedure entry point EVP_CIPHER_CTX_get_app_data could not be located in the dynamic link library libeay32.dll.`


Now test curl has been installed correctly.
Open up a new dos window, and enter the command `curl --version`, and verify that the correct version of curl is returned.


2) install Ruby
===============
Go to http://rubyinstaller.org/downloads/ and under the heading "RubyInstallers" download the package "Ruby 1.9.2-p290"

Run the exe file you have just downloaded, and accept the default install directory (eg `C:\Ruby192`)
Then add the following to your Path: `C:\Ruby191\bin`

Now test ruby and gem have been installed correctly
Open up a new dos window, and enter the command `ruby --version`, and verify that the correct version of ruby is returned.
Open up a new dos window, and enter the command `gem --version`, and verify that gem can return a version number.


3) install Ruby Development Kit (required so that the gem "curb" package can use the curl package installed in step 1)
===============================
Go to http://rubyinstaller.org/downloads/ and under the heading "Development Kit" download the package "DevKit-3.4.5-20100819-1535-sfx.exe"
Run the exe file and install in a directory with no space in the path name. Perhaps install in a directory something like `C:\rubyDevKit345`

Go to this page http://github.com/oneclick/rubyinstaller/wiki/Development-Kit and use the instructions to complete the installation of the devkit. To install the ruby devkit I only used instructions 3 and 4, that is:
    cd C:\rubyDevKit345
    ruby dk.rb init
    ruby dk.rb review
    ruby dk.rb install

4) Install bundler
===========================
Open a new dos window and run the following command
    gem install bundler --platform=ruby

The output will look something like:
    Temporarily enhancing PATH to include DevKit...
    Building native extensions.  This could take a while...
    Successfully installed bundler-1.0.18
    1 gem installed
    Installing ri documentation for bundler-1.0.18...
    Updating class cache with 0 classes...
    Installing RDoc documentation for bundler-1.0.18...

Note this command took quite some time to run on my laptop.

Check the bundler library has been installed, by running the following command "gem list".

And verify bundler is one of the libraries installed on your machine.


5) Have bundler install the ruby dependencies
=============================================

Run `bundle install` and bundler should fetch and install the ruby gems necessary.

6) Verify the above installation of every thing has worked
==========================================================

Use ruby program `create-user.rb` to add a new user. At the dos command prompt enter the following command `ruby create-user.rb testuser` as shown below, and you should see confirmation that a new user has been created.

    >ruby create-user.rb testuser
    User: testuser (pass: testuser)
    >
