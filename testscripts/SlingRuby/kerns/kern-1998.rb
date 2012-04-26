#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
include SlingUsers
include SlingFile

class TC_Kern1998Test < Test::Unit::TestCase
  include SlingTest

  def test_get_random_content_all_priority
    @fm = FileManager.new(@s)

    # create test users
    u1 = create_test_user(1998)
    @s.switch_user(u1)

    # create a new tag to work with
    m = uniqueness()
    tagname = "test#{m}"
    res = @s.execute_post(@s.url_for("/tags/#{tagname}"), {'_charset_' => 'utf8', 'sakai:tag-name' => tagname, 'sling:resourceType' => 'sakai/tag'})
    assert_equal('201', res.code, 'Should be able to create a new tag.')

    # add some content and tag each thing added
    4.times do |i|
      m = uniqueness()
      res = @fm.upload_pooled_file("random-#{m}.txt", 'Plain content', 'text/plain')
      assert_equal('201', res.code, 'Expected to be able to create pooled content')
      uploadresult = JSON.parse(res.body)
      poolid = uploadresult["random-#{m}.txt"]['poolId']

      res = @s.execute_post(@s.url_for("/p/#{poolid}"), {'_charset_' => 'utf8', ':operation' => 'tag', 'key' => "/tags/#{tagname}"})
      assert_equal('200', res.code, 'Should be able to tag content.')
    end

    # add some content but don't tag it to create the negative case
    2.times do |i|
      m = uniqueness()
      res = @fm.upload_pooled_file("random-#{m}.txt", 'Plain content', 'text/plain')
      assert_equal('201', res.code, 'Expected to be able to create pooled content')
    end

    wait_for_indexer()

    # get some random content
    res = @s.execute_get(@s.url_for('/var/search/public/random-content.json'))
    assert_equal('200', res.code, 'Feed should always return positively.')
    output = JSON.parse(res.body)
    results = output['results']
    assert_equal(4, results.length)

    priority = 0
    standard = 0
    results.each do |result|
      if is_priority?(result)
        priority += 1
      else
        standard += 1
      end
    end

    assert_equal(4, priority, 'Should have found only priority items')
    assert_equal(0, standard, 'Should have found no non-priority items')
  end

  # Should only be run on a clean data set as the feed being tested will pick up data from anywhere
  # in the system and that invalidates the test.
  def notest_get_random_content_some_priority
    @fm = FileManager.new(@s)

    # create test users
    u1 = create_test_user(1998)
    @s.switch_user(u1)

    # create a new tag to work with
    m = uniqueness()
    tagname = "test#{m}"
    res = @s.execute_post(@s.url_for("/tags/#{tagname}"), {'_charset_' => 'utf8', 'sakai:tag-name' => tagname, 'sling:resourceType' => 'sakai/tag'})
    assert_equal('201', res.code, 'Should be able to create a new tag.')

    # add some content and tag each thing added
    2.times do |i|
      m = uniqueness()
      res = @fm.upload_pooled_file("random-#{m}.txt", 'Plain content', 'text/plain')
      assert_equal('201', res.code, 'Expected to be able to create pooled content')
      uploadresult = JSON.parse(res.body)
      poolid = uploadresult["random-#{m}.txt"]['poolId']

      res = @s.execute_post(@s.url_for("/p/#{poolid}"), {'_charset_' => 'utf8', ':operation' => 'tag', 'key' => "/tags/#{tagname}"})
      assert_equal('200', res.code, 'Should be able to tag content.')
    end

    # add some content but don't tag it to create the negative case
    4.times do |i|
      m = uniqueness()
      res = @fm.upload_pooled_file("random-#{m}.txt", 'Plain content', 'text/plain')
      assert_equal('201', res.code, 'Expected to be able to create pooled content')
    end

    wait_for_indexer()

    # get some random content
    res = @s.execute_get(@s.url_for('/var/search/public/random-content.json'))
    assert_equal('200', res.code, 'Feed should always return positively.')
    output = JSON.parse(res.body)
    results = output['results']
    assert_equal(4, results.length)

    priority = 0
    standard = 0
    results.each do |result|
      if is_priority?(result)
        priority += 1
      else
        standard += 1
      end
    end

    assert_equal(2, priority, "Should have found some priority items #{priority}")
    assert_equal(2, standard, "Should have found some non-priority items #{standard}")
  end

  # Should only be run on a clean data set as the feed being tested will pick up data from anywhere
  # in the system and that invalidates the test.
  def notest_get_random_content_no_priority
    @fm = FileManager.new(@s)

    # create test users
    u1 = create_test_user(1998)
    @s.switch_user(u1)

    # add some content but don't tag it to create the negative case
    4.times do |i|
      m = uniqueness()
      res = @fm.upload_pooled_file("random-#{m}.txt", 'Plain content', 'text/plain')
      assert_equal('201', res.code, 'Expected to be able to create pooled content')
    end

    wait_for_indexer()

    # get some random content
    res = @s.execute_get(@s.url_for('/var/search/public/random-content.json'))
    assert_equal('200', res.code, 'Feed should always return positively.')
    output = JSON.parse(res.body)
    results = output['results']
    assert_equal(4, results.length)

    priority = 0
    standard = 0
    results.each do |result|
      if is_priority?(result)
        priority += 1
      else
        standard += 1
      end
    end

    assert_equal(0, priority, "Should have found no priority items #{priority}")
    assert_equal(4, standard, "Should have found only non-priority items #{standard}")
  end

  def is_priority?(result)
    if (!result['sakai:tags'].nil? && result['sakai:tags'].length >= 1) \
        || (!result['sakai:tag-uuid'].nil? && result['sakai:tag-uuid'].length >= 1) \
        || !result['sakai:description'].nil? || result['hasPreview'] == 'true'
      true
    else
      false
    end
  end
end
