#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura'
require 'nakamura/users'
include SlingInterface
include SlingUsers

if ARGV.size < 1
  puts "Usage: create_group.rb GROUPNAME"
  exit 1
end

groupname = ARGV[0]
@s = Sling.new()
@um = UserManager.new(@s)
puts @um.create_group(groupname)
