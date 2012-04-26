#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2196 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @m = uniqueness()
    @config_url = "/system/console/configMgr/org.sakaiproject.nakamura.lite.storage.jdbc.JDBCStorageClientPool"
    @config = get_config()

    @long_prop = "This is a prop that should exceed jdbc storage limit of 64K."
    while (@long_prop.length < 100000)
      @long_prop = @long_prop + "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    end
  end

  def test_long_string_with_short_limit
    set_long_string_size(1000)
    u = create_test_user("2196_short_limit")
    @s.switch_user(u)

    long_file_path = "~#{u.name}/test/kern2196/longpropwithshortlimit" + @m
    @s.execute_post(@s.url_for(long_file_path), {
        "foo"=> @long_prop
    })

    assert_equal(@long_prop, @s.get_node_props(long_file_path)["foo"], "Expected excessively long string property to be properly stored")

  end

  def test_long_string_with_long_limit
    set_long_string_size(200000)
    u = create_test_user("2196_larger_limit")
    @s.switch_user(u)

    long_file_path = "~#{u.name}/test/kern2196/longproplargerlimit" + @m
    res = @s.execute_post(@s.url_for(long_file_path), {
        "foo"=> @long_prop
    })

    # we expect a DataFormatException since 100000 is way too long for Derby strings
    assert_equal("500", res.code, "Expected a 500 due to DataFormatException since the prop is too long for native storage")
  end

  def set_long_string_size(size)
    params = {
        "action" => "ajaxConfigManager",
        "apply" => true,
        "propertylist" => "long-string-size",
        "long-string-size" => size
    }
    @s.switch_user(User.admin_user)
    @s.execute_post(@s.url_for(@config_url), params)
    # sleep 10 seconds so the stack can restart
    sleep(10)
  end

  def get_config
    @s.switch_user(User.admin_user)
    res = @s.execute_post(@s.url_for(@config_url), {})
    assert_equal(200, res.code.to_i)
    return JSON.parse(res.body)
  end

  def teardown
    # Reset the long string size to its default.
    set_long_string_size(0)
  end

end
