#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/search'
require 'test/unit.rb'
include SlingSearch

class TC_Kern294Test < Test::Unit::TestCase
  include SlingTest

  def test_move
    m = uniqueness()
    n1 = create_node("test/d1#{m}")
    #@s.debug = true
    res = @s.execute_post(@s.url_for(n1), { ":operation" => "move",
                                                 ":dest" => "d2#{m}" }) 
    #@s.debug = false
    assert_equal("201", res.code, "Expected to be able to move node")
  end

  def test_move_at_root
    m = uniqueness()
    n1 = create_node("d1#{m}")
    #@s.debug = true
    res = @s.execute_post(@s.url_for(n1), { ":operation" => "move",
                                                 ":dest" => "d2#{m}" }) 
    #@s.debug = false
    assert_equal("201", res.code, "Expected to be able to move node")
  end

end


