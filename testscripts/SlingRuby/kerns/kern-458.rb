#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'set'
require 'nakamura/test'
require 'nakamura/message'
include SlingSearch
include SlingMessage

class TC_Kern458Test < Test::Unit::TestCase
  include SlingTest
  

  
  def test_getdev
    # We create a test site.
    m = uniqueness()
    res = @s.execute_get(@s.url_for("/dev/"))
    assert_equal("text/html",res.content_type())
  end

end

