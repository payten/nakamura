#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/search'
require 'test/unit.rb'
include SlingSearch

class TC_MySearchTest < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @sm = SearchManager.new(@s)
  end

  def test_node_search
    m = uniqueness()
    filename = "anunusualstring #{m}"
    content = "words in a doc #{m}"
    create_pooled_content(filename, content)
    wait_for_indexer()

    # The 'filename' field is strict about matching so we should expect to get
    # first the document we want.
    result = @sm.search_for_file(filename)
    assert_not_nil(result, "Expected result back")
    assert_not_nil(result['results'], "Expected results back")
    nodes = result["results"]
    assert_equal(true, nodes.size > 0, "Expected same matching nodes")
    assert_equal(filename, nodes[0]["filename"], "Expected data to be loaded")

    # The 'content' field gets extra analysis when indexed so we have to sift
    # through the results to find the filename.
    result = @sm.search_for_file(content)
    assert_not_nil(result, "Expected result back")
    nodes = result["results"]
    assert_equal(true, nodes.size > 0, "Expected same matching nodes")
    found = false
    nodes.each do |node|
      if node['filename'] == filename
        found = true
        break
      end
    end
    assert(found, "Expected data to be loaded")
  end

  def test_user_search
    m = uniqueness()
    username = "unusualuser#{m}"
    create_user(username, "#{username}-firstname", "#{username}-lastname")

    wait_for_indexer()
    result = @sm.search_for_user("#{username}")
    assert_not_nil(result, "Expected result back")
    users = result["results"]
    assert_equal(1, users.size, "Expected one matching user [username]")
    assert_equal(username, users[0]["rep:userId"], "Expected user to match username")

    result = @sm.search_for_user("#{username}-firstname")
    assert_not_nil(result, "Expected result back")
    users = result["results"]
    assert_equal(1, users.size, "Expected one matching user [firstname]")
    assert_equal(username, users[0]["rep:userId"], "Expected user to match firstname")

    result = @sm.search_for_user("#{username}-lastname")
    assert_not_nil(result, "Expected result back")
    users = result["results"]
    assert_equal(1, users.size, "Expected one matching user [lastname]")
    assert_equal(username, users[0]["rep:userId"], "Expected user to match lastname")
  end

end
