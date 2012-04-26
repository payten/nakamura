#!/usr/bin/env ruby

require 'rubygems'
require 'bundler/setup'
require 'daemons'

Daemons.run('preview_processor.rb')
