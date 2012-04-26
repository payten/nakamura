#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2243 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @m = uniqueness()

    @shared_props = {
        "booleanProp"=> true,
        "booleanProp@TypeHint" => "Boolean",
        "booleanishString" => true,
        "booleanArrayProp" => [false, true],
        "booleanArrayProp@TypeHint" => "Boolean",
        "integerProp"=>1,
        "integerProp@TypeHint" => "Integer",
        "integerishString" => 1,
        "integerArrayProp" => [1, 2, -5],
        "integerArrayProp@TypeHint" => "Integer",
        "longProp"=>9223372036854775807,
        "longProp@TypeHint" => "Long",
        "longishString" => 9223372036854775807,
        "longArrayProp" => [9223372036854775807, 0, -9223372036854775808],
        "longArrayProp@TypeHint" => "Long",
        "doubleProp"=>1.7976931348623157,
        "doubleProp@TypeHint" => "Double",
        "doublishString"=>1.7976931,
        "doubleArrayProp" => [0.0, 1.0, -1.0],
        "doubleArrayProp@TypeHint" => "Double",
        "decimalProp"=> 27.812,
        "decimalProp@TypeHint"=>"Decimal",
        "decimalishString"=> 92233720368547758079223372036854775807,
        "decimalArrayProp"=> [12.2, 3.14],
        "decimalArrayProp@TypeHint"=>"Decimal",
        "stringProp" => "something",
        "stringProp@TypeHint" => "String",
        "stringArrayProp" => ["something", "else"],
        "stringArrayProp@TypeHint" => "String",
        "dateProp" => "01.01.2010",
        "dateProp@TypeHint" => "Date",
        "dateArrayProp" => ["01.01.2010", "01.01.2011"],
        "dateArrayProp@TypeHint" => "Date"
    }
  end

  # exercise all the @TypeHint values that sparse supports and our servlet knows about, going
  # through the default create servlet (backed by ModifyOperation)
  def test_typehints_on_default_servlet

    u = create_test_user("kern2243")
    @s.switch_user(u)

    path = "~#{u.name}/test/kern2243" + @m
    @s.execute_post(@s.url_for(path), @shared_props)
    props = @s.get_node_props(path)
    check_asserts(props)
  end

  # check the TypeHint support on the user-creation servlet (backed by LiteAbstractAuthorizablePostServlet and children)
  def test_typehints_on_user_servlet
    $USER_CREATE_URI="system/userManager/user.create.html"
    username = "kern2243_user_servlet" + @m
    authorizable_path = "system/userManager/user/#{username}"
    userdata = {
        ":name"=>username,
        "pwd"=>"s",
        "pwdConfirm"=>"s"
    }

    @s.execute_post(@s.url_for($USER_CREATE_URI), userdata.merge(@shared_props))
    props = @s.get_node_props(authorizable_path)
    check_asserts(props)

  end

  def check_asserts(props)

    @log.info(props)

    # booleans
    assert_equal(true, props["booleanProp"])
    assert_equal("true", props["booleanishString"])
    assert_equal([false, true], props["booleanArrayProp"])

    # integers
    assert_equal(1, props["integerProp"])
    assert_equal("1", props["integerishString"])
    assert_equal([1, 2, -5], props["integerArrayProp"])

    # longs
    assert_equal(9223372036854775807, props["longProp"])
    assert_equal("9223372036854775807", props["longishString"])
    assert_equal([9223372036854775807, 0, -9223372036854775808], props["longArrayProp"])

    # doubles
    delta = 1.7976931348623157 - props["doubleProp"]
    assert(delta.abs < 0.001)
    assert_equal("1.7976931", props["doublishString"])
    assert_equal([0.0, 1.0, -1.0], props["doubleArrayProp"])

    # big decimals
    assert_equal("27.812", props["decimalProp"])
    assert_equal("92233720368547758079223372036854775807", props["decimalishString"])
    assert_equal(["12.2", "3.14"], props["decimalArrayProp"])

    # strings
    assert_equal("something", props["stringProp"])
    assert_equal(["something", "else"], props["stringArrayProp"])

    # dates
    dt_offset = Time.new(2010,1,1).strftime('%z')
    assert_equal("Fri Jan 01 2010 00:00:00 GMT#{dt_offset}", props["dateProp"])
    assert_equal(["Fri Jan 01 2010 00:00:00 GMT#{dt_offset}", "Sat Jan 01 2011 00:00:00 GMT#{dt_offset}"], props["dateArrayProp"])

  end
end
