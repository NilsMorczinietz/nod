import { mount } from 'svelte'
import { installErrorReporting } from '../lib/diagnostics'
import '../styles/base.css'
import App from './App.svelte'

installErrorReporting()
mount(App, { target: document.getElementById('app')! })
