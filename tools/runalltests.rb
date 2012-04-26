#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'find'

Find.find("#{File.dirname(__FILE__)}/..") do |path|
  if FileTest.directory?(path)
    if File.basename(path) == "target"
      Find.prune
    else
      next
    end
  else
    if File.basename(path) == "testall.rb"
      load(path, true)
    end
  end
end

