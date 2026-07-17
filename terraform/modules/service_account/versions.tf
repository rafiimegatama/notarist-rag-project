# Provider and version constraints for this module.
#
# Kept in step with the environment roots (terraform/environments/*/main.tf). A module that does not
# pin its own provider inherits whatever the calling root happens to have, which makes a module
# silently behave differently depending on who calls it.

terraform {
  required_version = ">= 1.5"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}
